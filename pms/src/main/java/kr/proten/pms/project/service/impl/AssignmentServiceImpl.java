package kr.proten.pms.project.service.impl;

import java.util.Map;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.person.service.PersonDirectoryService;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.service.AssignmentService;
import kr.proten.pms.project.service.dto.AssignmentView;
import kr.proten.pms.project.service.dto.CreateAssignmentCommand;
import kr.proten.pms.project.service.dto.UpdateAssignmentCommand;
import kr.proten.pms.project.service.entity.AssignmentStatus;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAction;
import kr.proten.pms.project.service.entity.ProjectAssignment;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인력 배정 유스케이스 — AC B1-1·B1-2·B1-4·B2-1.
 *
 * 배정은 프로젝트에 매달린 자원이라 판정도 프로젝트를 통해 한다: 배정 id로 들어와도
 * 먼저 프로젝트 가시성(404 은닉)을 통과해야 하고, 권한은 프로젝트 역할이 정한다
 * (상위 PRD §4-2 `ASSIGN` = PM). 배정 id의 존재 여부만으로 응답이 갈리면 가시성
 * 밖 프로젝트의 배정 수를 헤아릴 수 있다.
 *
 * 가동률 재계산(AC B1-3·C1-4)은 resource 모듈이 아직 없어 검증 대상이 아니다 —
 * 그 모듈이 생기면 커밋 후 이벤트로 잇는다.
 */
@Service
@Transactional
public class AssignmentServiceImpl implements AssignmentService {
    private final ProjectAssignmentRepository assignmentRepository;
    private final ProjectVisibilityService projectVisibilityService;
    private final ProjectActionPermission projectActionPermission;
    private final PersonDirectoryService personDirectoryService;
    private final AssignmentFactory assignmentFactory;
    private final ProjectAuditRecorder projectAuditRecorder;
    private final ProjectViewFactory projectViewFactory;

    public AssignmentServiceImpl(
            ProjectAssignmentRepository assignmentRepository,
            ProjectVisibilityService projectVisibilityService,
            ProjectActionPermission projectActionPermission,
            PersonDirectoryService personDirectoryService,
            AssignmentFactory assignmentFactory,
            ProjectAuditRecorder projectAuditRecorder,
            ProjectViewFactory projectViewFactory) {
        this.assignmentRepository = assignmentRepository;
        this.projectVisibilityService = projectVisibilityService;
        this.projectActionPermission = projectActionPermission;
        this.personDirectoryService = personDirectoryService;
        this.assignmentFactory = assignmentFactory;
        this.projectAuditRecorder = projectAuditRecorder;
        this.projectViewFactory = projectViewFactory;
    }

    public AssignmentView assign(long callerPersonId, CreateAssignmentCommand command) {
        Project project =
                projectVisibilityService.requireVisible(callerPersonId, command.projectId());
        projectActionPermission.require(callerPersonId, project.getId(), ProjectAction.ASSIGN);
        requireAssignableRole(command.role());
        requireKnownPerson(command.personId());
        requireNotAlreadyAssigned(project.getId(), command.personId());

        ProjectAssignment saved =
                assignmentRepository.save(assignmentFactory.create(project, command.toSpec()));
        projectAuditRecorder.assignmentCreated(callerPersonId, saved);

        return projectViewFactory.toView(saved);
    }

    public AssignmentView update(long callerPersonId, UpdateAssignmentCommand command) {
        ProjectAssignment assignment = requireAssignment(command.assignmentId());
        requireAssignPermission(callerPersonId, assignment);
        assignment.requireVersion(command.version());

        Map<String, Object> before = projectAuditRecorder.snapshot(assignment);
        // 미지정은 미지정으로 저장한다 — 프로젝트 기간으로 채우는 기본값(A6-6)은
        // 새 배정에만 적용되고, 수정은 준 값이 그대로 남아야 한다(PUT 의미론)
        assignment.reschedule(command.startDate(), command.endDate(), command.monthlyMm());
        ProjectAssignment saved = assignmentRepository.saveAndFlush(assignment);
        projectAuditRecorder.assignmentChanged(callerPersonId, saved, before);

        return projectViewFactory.toView(saved);
    }

    public void close(long callerPersonId, long assignmentId) {
        ProjectAssignment assignment = requireAssignment(assignmentId);
        requireAssignPermission(callerPersonId, assignment);
        requireNotManager(assignment);

        Map<String, Object> before = projectAuditRecorder.snapshot(assignment);
        assignment.close();
        ProjectAssignment saved = assignmentRepository.saveAndFlush(assignment);
        projectAuditRecorder.assignmentClosed(callerPersonId, saved, before);
    }

    /** 배정 부재와 가시성 밖 프로젝트의 배정은 같은 404다 (은닉 — A3-2와 같은 의미론). */
    private ProjectAssignment requireAssignment(long assignmentId) {
        return assignmentRepository.findById(assignmentId).orElseThrow(NotFoundException::new);
    }

    private void requireAssignPermission(long callerPersonId, ProjectAssignment assignment) {
        Project project =
                projectVisibilityService.requireVisible(callerPersonId, assignment.getProjectId());
        projectActionPermission.require(callerPersonId, project.getId(), ProjectAction.ASSIGN);
    }

    /**
     * PM 배정은 이 경로로 만들 수 없다 (AC A6-7과 같은 이유).
     * 프로젝트당 role=PM 정확히 1행 불변식은 PM 교체 전용 경로만이 지킬 수 있다.
     */
    private void requireAssignableRole(ProjectRole role) {
        if (role == ProjectRole.PM) {
            throw new UnprocessableException("INVALID_ROLE",
                    "PM 지정은 PM 교체 경로로만 가능합니다");
        }
    }

    private void requireKnownPerson(Long personId) {
        if (!personDirectoryService.existsActive(personId)) {
            throw new UnprocessableException("REF_NOT_FOUND", "없는 인원입니다: " + personId);
        }
    }

    /** 중복 배정 판정 (AC B1-2) — 키는 projectId + personId + 종료 아님이다. */
    private void requireNotAlreadyAssigned(Long projectId, Long personId) {
        boolean assigned = assignmentRepository.existsByProjectIdAndPersonIdAndStatus(
                projectId, personId, AssignmentStatus.ACTIVE);

        if (assigned) {
            throw new ConflictException("DUPLICATE_ASSIGNMENT", "이미 배정된 인원입니다");
        }
    }

    /**
     * PM 배정은 종료할 수 없다 — 종료하면 프로젝트에 PM이 없는 상태가 되어
     * 불변식(A6-5)이 깨진다. PM을 교체한 뒤 종료하는 것이 순서다.
     */
    private void requireNotManager(ProjectAssignment assignment) {
        if (assignment.isManager()) {
            throw new UnprocessableException("INVALID_ROLE",
                    "PM 배정은 종료할 수 없습니다 — PM을 교체한 뒤 종료하세요");
        }
    }
}
