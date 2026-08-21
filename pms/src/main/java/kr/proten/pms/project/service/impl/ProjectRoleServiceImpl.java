package kr.proten.pms.project.service.impl;

import java.util.Map;
import java.util.Optional;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.person.service.PersonDirectoryService;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.ProjectRoleService;
import kr.proten.pms.project.service.dto.AssignmentSpec;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.entity.AssignmentStatus;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAction;
import kr.proten.pms.project.service.entity.ProjectAssignment;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PM 교체 — AC A6-1·A6-2·A6-4·A6-5.
 *
 * 한 트랜잭션에서 셋을 함께 한다: 새 PM 승격(없으면 배정 생성) · 직전 PM을 참여자로
 * 강등(배정은 유지) · `Project.managerId` 동기화. 하나라도 빠지면 "프로젝트당 PM 1행 ·
 * managerId와 일치"(A6-5)가 깨진다.
 * 권한은 배정 권한과 같은 PM 전용이다 — 자원 배분 경로를 한 역할로 좁힌다(§4-2).
 */
@Service
@Transactional
public class ProjectRoleServiceImpl implements ProjectRoleService {
    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository assignmentRepository;
    private final ProjectVisibilityService projectVisibilityService;
    private final ProjectActionPermission projectActionPermission;
    private final PersonDirectoryService personDirectoryService;
    private final AssignmentFactory assignmentFactory;
    private final ProjectAuditRecorder projectAuditRecorder;
    private final ProjectViewFactory projectViewFactory;

    public ProjectRoleServiceImpl(
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
            throw new UnprocessableException("REF_NOT_FOUND", "없는 인원입니다: " + personId);
        }
    }

    /**
     * 이미 PM인 사람을 다시 PM으로 지정하는 요청은 거절한다 — 아무것도 바뀌지 않는
     * 요청이 성공으로 보이면 화면이 "교체됐다"고 잘못 알린다.
     */
    private void requireDifferentPerson(Project project, long personId) {
        if (project.getManagerId() == personId) {
            throw new UnprocessableException("INVALID_ROLE", "이미 이 프로젝트의 PM입니다");
        }
    }
}
