package kr.proten.pms.project.service.impl;

import java.util.Map;
import java.util.function.Consumer;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.ProjectCompletionService;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 완료 처리·재개 유스케이스 — AC A7-1~A7-5.
 *
 * 두 경로가 판정 순서·감사 기록까지 같아서 전이 동작만 다르게 넘긴다 —
 * 순서(가시성 404 은닉 → 권한 403 → version 409 → 전이 규칙 409)를 한 곳에 두면
 * 한쪽만 어긋나는 일이 생기지 않는다. 전이 규칙 자체는 엔티티가 갖는다(§5).
 */
@Service
@Transactional
public class ProjectCompletionServiceImpl implements ProjectCompletionService {
    private final ProjectRepository projectRepository;
    private final ProjectVisibilityService projectVisibilityService;
    private final ProjectActionPermission projectActionPermission;
    private final ProjectAuditRecorder projectAuditRecorder;
    private final ProjectViewFactory projectViewFactory;

    public ProjectCompletionServiceImpl(
            ProjectRepository projectRepository,
            ProjectVisibilityService projectVisibilityService,
            ProjectActionPermission projectActionPermission,
            ProjectAuditRecorder projectAuditRecorder,
            ProjectViewFactory projectViewFactory) {
        this.projectRepository = projectRepository;
        this.projectVisibilityService = projectVisibilityService;
        this.projectActionPermission = projectActionPermission;
        this.projectAuditRecorder = projectAuditRecorder;
        this.projectViewFactory = projectViewFactory;
    }

    public ProjectDetail complete(long callerPersonId, long projectId, long version) {
        return transition(callerPersonId, projectId, version, Project::complete);
    }

    public ProjectDetail reopen(long callerPersonId, long projectId, long version) {
        return transition(callerPersonId, projectId, version, Project::reopen);
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
}
