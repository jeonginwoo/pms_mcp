package kr.proten.pms.project.service.impl;

import java.util.Map;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.ProjectDeleteService;
import kr.proten.pms.project.service.entity.Project;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 삭제 유스케이스 — AC A4-1·A4-2.
 * 순서는 가시성(404 은닉) → 권한(403)이고, version은 받지 않는다 — 삭제는 "어떤 값으로
 * 바꿀지"가 없는 행위라 동시 수정 충돌의 대상이 아니다(A4-1도 version을 요구하지 않는다).
 */
@Service
@Transactional
public class ProjectDeleteServiceImpl implements ProjectDeleteService {
    private final ProjectRepository projectRepository;
    private final ProjectVisibilityService projectVisibilityService;
    private final ProjectActionPermission projectActionPermission;
    private final ProjectAuditRecorder projectAuditRecorder;

    public ProjectDeleteServiceImpl(
            ProjectRepository projectRepository,
            ProjectVisibilityService projectVisibilityService,
            ProjectActionPermission projectActionPermission,
            ProjectAuditRecorder projectAuditRecorder) {
        this.projectRepository = projectRepository;
        this.projectVisibilityService = projectVisibilityService;
        this.projectActionPermission = projectActionPermission;
        this.projectAuditRecorder = projectAuditRecorder;
    }

    public void delete(long callerPersonId, long projectId) {
        Project project = projectVisibilityService.requireVisible(callerPersonId, projectId);
        projectActionPermission.requireDelete(callerPersonId, projectId);

        Map<String, Object> before = projectAuditRecorder.snapshot(project);
        project.delete();
        projectAuditRecorder.deleted(callerPersonId, projectRepository.saveAndFlush(project),
                before);
    }
}
