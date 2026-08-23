package kr.proten.pms.project.service.impl;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.person.PersonDirectoryService;
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
import kr.proten.pms.project.service.entity.ProjectStatus;
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

    public ProjectLifecycleServiceImpl(
            ProjectRepository projectRepository,
            ProjectAssignmentRepository assignmentRepository,
            ProjectVisibilityService projectVisibilityService,
            ProjectActionPermission projectActionPermission,
            PersonDirectoryService personDirectoryService,
            AssignmentFactory assignmentFactory,
            ProjectAuditRecorder projectAuditRecorder,
            ProjectViewFactory projectViewFactory) {
        this.projectRepository = projectRepository;
        this.assignmentRepository = assignmentRepository;
        this.projectVisibilityService = projectVisibilityService;
        this.projectActionPermission = projectActionPermission;
        this.personDirectoryService = personDirectoryService;
        this.assignmentFactory = assignmentFactory;
        this.projectAuditRecorder = projectAuditRecorder;
        this.projectViewFactory = projectViewFactory;
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
    private void requireDifferentPerson(Project project, long personId) {
        if (project.getManagerId() == personId) {
            throw new UnprocessableException(ErrorCode.INVALID_ROLE, "이미 이 프로젝트의 PM입니다");
        }
    }
}
