package kr.proten.pms.project.service.impl;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.project.HandoverPort;
import kr.proten.pms.project.HandoverSpec;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.ProjectLifecycleService;
import kr.proten.pms.project.service.dto.AssignmentSpec;
import kr.proten.pms.project.service.dto.ProgressUpdateResult;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.dto.UpdateProgressCommand;
import kr.proten.pms.project.service.entity.AssignmentStatus;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAction;
import kr.proten.pms.project.service.entity.ProjectAssignment;
import kr.proten.pms.project.service.entity.ProjectRole;
import kr.proten.pms.project.ProjectStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 생애주기 유스케이스 — AC A2 · A6-1 · A7.
 *
 * 네 행위가 한 클래스인 이유는 순서가 같기 때문이다: 가시성(404 은닉) → 권한(403) →
 * version(409) → 도메인 규칙(409·422) → 감사 1행. 순서를 한 곳에 두면 한쪽만
 * 어긋나는 일이 생기지 않는다. 전이 규칙 자체는 엔티티가 갖는다(§5).
 *
 * 감사 action이 갈리는 지점도 여기 모여 있다: 완료·재개는 상태가 바뀌므로
 * STATE_CHANGE(§5 "모든 전이"), 진척률과 PM 교체는 상태가 아니므로 UPDATE다
 * (A2-2 · A6-1). 판정은 `ProjectAuditRecorder`가 스냅샷 차이로 하므로 호출부가
 * action을 고르지 않는다.
 */
@Service
@Transactional
public class ProjectLifecycleServiceImpl implements ProjectLifecycleService {
    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository assignmentRepository;
    private final ProjectVisibilityService projectVisibilityService;
    private final ProjectActionPermission projectActionPermission;
    private final PersonDirectoryService personDirectoryService;
    private final AssignmentFactory assignmentFactory;
    private final ProjectAuditRecorder projectAuditRecorder;
    private final ProjectViewFactory projectViewFactory;
    private final HandoverPort handoverPort;

    public ProjectLifecycleServiceImpl(
            ProjectRepository projectRepository,
            ProjectAssignmentRepository assignmentRepository,
            ProjectVisibilityService projectVisibilityService,
            ProjectActionPermission projectActionPermission,
            PersonDirectoryService personDirectoryService,
            AssignmentFactory assignmentFactory,
            ProjectAuditRecorder projectAuditRecorder,
            ProjectViewFactory projectViewFactory,
            HandoverPort handoverPort) {
        this.projectRepository = projectRepository;
        this.assignmentRepository = assignmentRepository;
        this.projectVisibilityService = projectVisibilityService;
        this.projectActionPermission = projectActionPermission;
        this.personDirectoryService = personDirectoryService;
        this.assignmentFactory = assignmentFactory;
        this.projectAuditRecorder = projectAuditRecorder;
        this.projectViewFactory = projectViewFactory;
        this.handoverPort = handoverPort;
    }

    /**
     * 진척률을 확인 후 갱신한다 (AC A2-1~A2-9).
     *
     * 2단계 확인이 이 서비스의 프로토콜이다: confirmed=false는 변경 요약만 돌려주고
     * DB를 건드리지 않으며, confirmed=true에서 낙관적 락을 검사하고 커밋한다.
     * 웹 UI와 MCP `update_progress`가 같은 서비스를 쓰므로(US-A2) 프로토콜이 여기
     * 한 곳에 있다 — 입구마다 다른 확인 절차를 만들면 권한·감사가 갈라진다.
     * 검사 순서는 형식(400) → 가시성(404) → 권한(403) → 상태(409)다.
     */
    public ProgressUpdateResult updateProgress(
            long callerPersonId, UpdateProgressCommand command) {
        requireProgressInRange(command.progress());

        Project project =
                projectVisibilityService.requireVisible(callerPersonId, command.projectId());
        // 배정 인원이면 역할 무관하게 가능하다 (상위 PRD §4-2 기본 매트릭스) —
        // 진척률은 Project.progress 단일 값이라 "본인 몫" 부분 수정 개념이 없다
        projectActionPermission.require(callerPersonId, project.getId(),
                ProjectAction.PROGRESS);
        requireInProgress(project);

        if (!command.confirmed()) {
            return ProgressUpdateResult.summary(project, command.progress());
        }

        project.requireVersion(command.version());

        Map<String, Object> before = projectAuditRecorder.snapshot(project);
        project.updateProgress(command.progress());
        Project saved = projectRepository.saveAndFlush(project);
        // 상태는 바뀌지 않으므로 UPDATE로 남는다 (AC A2-2 · §5 자동 전이 폐지)
        projectAuditRecorder.changed(callerPersonId, saved, before);

        return ProgressUpdateResult.committedOf(saved);
    }

    /** 완료 처리 (AC A7-1) — 진행중·진척률 100%가 전제다. */
    public ProjectDetail complete(long callerPersonId, long projectId, long version) {
        return transition(callerPersonId, projectId, version, Project::complete);
    }

    /** 재개 (AC A7-3) — 완료 → 진행중, 진척률은 90으로 돌아간다. */
    public ProjectDetail reopen(long callerPersonId, long projectId, long version) {
        return transition(callerPersonId, projectId, version, Project::reopen);
    }

    /**
     * PM 교체 (AC A6-1·A6-2·A6-4·A6-5).
     *
     * 한 트랜잭션에서 셋을 함께 한다: 새 PM 승격(없으면 배정 생성) · 직전 PM을
     * 참여자로 강등(배정은 유지) · `Project.managerId` 동기화. 하나라도 빠지면
     * "프로젝트당 PM 1행 · managerId와 일치"(A6-5)가 깨진다.
     * 권한은 배정과 같은 PM 전용이다 — 자원 배분 경로를 한 역할로 좁힌다(§4-2).
     */
    public ProjectDetail changeManager(
            long callerPersonId,
            long projectId,
            long personId,
            long version) {
        Project project = projectVisibilityService.requireVisible(callerPersonId, projectId);
        projectActionPermission.require(callerPersonId, projectId, ProjectAction.ASSIGN);
        requireKnownPerson(personId);
        project.requireVersion(version);
        requireDifferentPerson(project, personId);

        demoteCurrentManager(projectId, personId);
        promote(project, personId);

        Map<String, Object> before = projectAuditRecorder.snapshot(project);
        project.changeManager(personId);
        Project saved = projectRepository.saveAndFlush(project);
        // 상태가 아니라 담당이 바뀌었으므로 UPDATE다 (A6-1 — STATE_CHANGE는 §5 전용)
        projectAuditRecorder.changed(callerPersonId, saved, before);

        return projectViewFactory.toDetail(saved);
    }

    /**
     * 역할 지정·교체 (AC A6-3·A6-6·A6-7).
     *
     * 권한은 PM 교체와 같은 `ASSIGN`이다 — 역할 지정은 자원 배분이라 한 역할로 좁힌다(§4-2).
     *
     * `version`을 받지 않는다: AC의 요청 본문이 `{personId, role}`이고, 바뀌는 행은
     * 프로젝트가 아니라 **배정**이다(감사도 `ProjectAssignment` 엔티티로 남는다).
     * 프로젝트의 version을 걸면 배정 목록을 고칠 때마다 프로젝트 version이 올라
     * 열려 있던 다른 폼이 409를 받는다.
     *
     * 같은 역할을 다시 지정하는 요청은 그대로 성공한다 — AC에 없는 거절을 만들지 않고,
     * 바뀐 것이 없으면 감사 행도 남지 않는다(`recordDiff`가 빈 diff를 버린다).
     */
    public ProjectDetail changeRole(
            long callerPersonId,
            long projectId,
            long personId,
            ProjectRole role) {
        Project project = projectVisibilityService.requireVisible(callerPersonId, projectId);
        projectActionPermission.require(callerPersonId, projectId, ProjectAction.ASSIGN);
        requireAssignableRole(role);
        requireKnownPerson(personId);
        requireNotCurrentManager(project, personId);

        Optional<ProjectAssignment> existing = assignmentRepository
                .findByProjectIdAndPersonIdAndStatus(projectId, personId, AssignmentStatus.ACTIVE);

        if (existing.isPresent()) {
            ProjectAssignment assignment = existing.get();
            Map<String, Object> before = projectAuditRecorder.snapshot(assignment);
            assignment.changeRole(role);
            projectAuditRecorder.assignmentChanged(callerPersonId,
                    assignmentRepository.saveAndFlush(assignment), before);
        } else {
            // 미배정 대상에게 역할을 주면 배정을 함께 만든다 (A6-6) — PM·PL은 항상
            // 배정 인원이고(§4-2), 참여자도 배정 없이 역할만 갖는 상태가 없다
            ProjectAssignment created = assignmentRepository.save(assignmentFactory.create(
                    project, new AssignmentSpec(personId, role, null, null, 0.0)));
            projectAuditRecorder.assignmentCreated(callerPersonId, created);
        }

        return projectViewFactory.toDetail(project);
    }

    /**
     * 이관 (AC D1-1·D1-2·D1-3) — 이 클래스에서 <b>순서가 다른 유일한 행위</b>다.
     *
     * <p>나머지 셋은 도메인 규칙이 마지막이지만 이관은 그 사이에 <b>모듈 하나를
     * 건너간다</b>. 순서가 AC 두 줄로 정해져 있다:
     *
     * <ul>
     *   <li>D1-2 "아무것도 안 바뀜" → <b>상태 확인이 계약 생성보다 앞</b>이다.
     *       완료가 아닌 프로젝트로 이관을 시도하면 계약도 만들어지지 않아야 한다.
     *   <li>D1-3 "상태 전이도 미발생" → <b>입력 검증이 상태 전이보다 앞</b>이다.
     *       필수값이 모자라면 400이고 프로젝트는 완료로 남는다.
     * </ul>
     *
     * <p>그래서 {@code handover()}를 두 번 나눠 부르지 않고 <b>상태 확인을 먼저
     * 읽는다</b>: 엔티티의 전이 메서드는 확인과 변경을 함께 하므로, 그것만으로는
     * "확인은 했고 아직 안 바꿨다"는 중간 상태를 만들 수 없다.
     *
     * <p>감사는 두 행이다 — 계약 CREATE(maintenance가 남긴다, {@code projectId=null})와
     * 프로젝트 STATE_CHANGE(여기서 남긴다, {@code projectId} 있음). 이관 사실을
     * 프로젝트별 이력(G2-2)에서 찾는 경로는 후자다.
     */
    @Override
    public ProjectDetail handover(
            long callerPersonId, long projectId, HandoverSpec spec, long version) {
        Project project = projectVisibilityService.requireVisible(callerPersonId, projectId);
        projectActionPermission.require(callerPersonId, projectId, ProjectAction.HANDOVER);
        project.requireVersion(version);
        // D1-2 — 완료가 아니면 여기서 끝난다. 계약을 만들기 전이다
        requireCompleted(project);
        // D1-3 — 필수값 검증도 전이 전이다. 포트 구현이 400을 던지면 전이는 없다
        handoverPort.createHandoverContract(callerPersonId, projectId, spec);

        Map<String, Object> before = projectAuditRecorder.snapshot(project);
        project.handover();
        Project saved = projectRepository.saveAndFlush(project);
        projectAuditRecorder.changed(callerPersonId, saved, before);

        return projectViewFactory.toDetail(saved);
    }

    /**
     * D1-2의 앞당긴 확인 — {@code Project.handover()}가 같은 검사를 다시 하지만,
     * 그때는 이미 계약이 만들어진 뒤다. 같은 규칙이 두 곳에 있는 것이 아니라
     * <b>같은 규칙을 두 시점에 묻는 것</b>이고, 정본은 엔티티다(문구도 거기서 온다).
     */
    private void requireCompleted(Project project) {
        if (project.getStatus() != ProjectStatus.COMPLETED) {
            throw new ConflictException(ErrorCode.INVALID_TRANSITION,
                    "완료된 프로젝트만 유지보수로 이관할 수 있습니다 (현재 "
                            + project.getStatus().label() + ")");
        }
    }

    private ProjectDetail transition(
            long callerPersonId,
            long projectId,
            long version,
            Consumer<Project> transition) {
        Project project = projectVisibilityService.requireVisible(callerPersonId, projectId);
        projectActionPermission.require(callerPersonId, projectId,
                ProjectAction.COMPLETE_REOPEN);
        project.requireVersion(version);

        Map<String, Object> before = projectAuditRecorder.snapshot(project);
        transition.accept(project);
        Project saved = projectRepository.saveAndFlush(project);
        // 상태가 바뀌었으므로 STATE_CHANGE로 남는다 (§5 "모든 전이 AuditLog STATE_CHANGE")
        projectAuditRecorder.changed(callerPersonId, saved, before);

        return projectViewFactory.toDetail(saved);
    }

    private void requireProgressInRange(int progress) {
        if (progress < 0 || progress > 100) {
            throw new ValidationException("진척률은 0에서 100 사이여야 합니다", "progress");
        }
    }

    /**
     * 진척률은 진행중 상태에서만 수정한다 (2026-08-22 결정).
     *
     * 완료는 기존 코드(A2-8 `PROJECT_COMPLETED`)를 그대로 쓴다 — 재개하면 다시 수정할
     * 수 있다는 안내가 달린 별개의 상황이다. 그 밖의 상태(계약대기·수주확정·유지보수중)는
     * `NOT_IN_PROGRESS`: 아직/이미 진척을 기록할 단계가 아니라 상태를 먼저 옮겨야 한다.
     * MCP `update_progress`도 같은 서비스라 같은 거절을 받는다.
     */
    private void requireInProgress(Project project) {
        if (project.isCompleted()) {
            throw new ConflictException(ErrorCode.PROJECT_COMPLETED,
                    "완료된 프로젝트는 진척률을 수정할 수 없습니다 — 재개 후 수정하세요");
        }

        if (project.getStatus() != ProjectStatus.IN_PROGRESS) {
            throw new ConflictException(ErrorCode.NOT_IN_PROGRESS,
                    "진행중 프로젝트만 진척률을 수정할 수 있습니다 (현재 "
                            + project.getStatus().label() + ")");
        }
    }

    /** 직전 PM은 참여자로 남긴다 (AC A6-4) — 배정을 끊지 않는 이유는 이력·가동률 보존이다. */
    private void demoteCurrentManager(long projectId, long newManagerId) {
        assignmentRepository
                .findByProjectIdAndRoleAndStatus(projectId, ProjectRole.PM, AssignmentStatus.ACTIVE)
                .stream()
                .filter(assignment -> !assignment.getPersonId().equals(newManagerId))
                .forEach(assignment -> assignment.changeRole(ProjectRole.PARTICIPANT));
    }

    /** 미배정 대상은 배정을 함께 만든다 (AC A6-4·A6-6 기본값: 프로젝트 기간·M/M 0). */
    private void promote(Project project, long personId) {
        Optional<ProjectAssignment> existing = assignmentRepository
                .findByProjectIdAndPersonIdAndStatus(project.getId(), personId,
                        AssignmentStatus.ACTIVE);

        if (existing.isPresent()) {
            existing.get().changeRole(ProjectRole.PM);

            return;
        }

        assignmentRepository.save(assignmentFactory.create(project,
                new AssignmentSpec(personId, ProjectRole.PM, null, null, 0.0)));
    }

    private void requireKnownPerson(long personId) {
        if (!personDirectoryService.existsActive(personId)) {
            throw new UnprocessableException(ErrorCode.REF_NOT_FOUND, "없는 인원입니다: " + personId);
        }
    }

    /**
     * 이미 PM인 사람을 다시 PM으로 지정하는 요청은 거절한다 — 아무것도 바뀌지 않는
     * 요청이 성공으로 보이면 화면이 "교체됐다"고 잘못 알린다.
     */
    /** PM은 `/roles`로 지정하지 않는다 (AC A6-7) — A6-5 불변식 우회 차단. */
    private void requireAssignableRole(ProjectRole role) {
        if (role == ProjectRole.PM) {
            throw new UnprocessableException(ErrorCode.INVALID_ROLE,
                    "PM은 이 경로로 지정할 수 없습니다 — PM 교체를 쓰세요");
        }
    }

    /**
     * 현 PM을 PL·참여자로 내리는 요청도 거절한다 (A6-5 불변식).
     *
     * 이 경로로 허용하면 PM이 없는 프로젝트가 생긴다 — 새 PM 승격과 직전 PM 강등을
     * 한 트랜잭션에서 하는 {@link #changeManager}만이 불변식을 지키며 PM을 바꾼다.
     */
    private void requireNotCurrentManager(Project project, long personId) {
        if (project.getManagerId() == personId) {
            throw new UnprocessableException(ErrorCode.INVALID_ROLE,
                    "현 PM의 역할은 이 경로로 바꿀 수 없습니다 — 다른 사람을 PM으로 교체하세요");
        }
    }

    private void requireDifferentPerson(Project project, long personId) {
        if (project.getManagerId() == personId) {
            throw new UnprocessableException(ErrorCode.INVALID_ROLE, "이미 이 프로젝트의 PM입니다");
        }
    }
}
