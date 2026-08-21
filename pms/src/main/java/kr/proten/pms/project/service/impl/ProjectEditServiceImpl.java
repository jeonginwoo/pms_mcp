package kr.proten.pms.project.service.impl;

import java.util.Map;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.ProjectEditService;
import kr.proten.pms.project.service.dto.EditProjectCommand;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAction;
import kr.proten.pms.project.service.entity.ProjectKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 정보·상태 수정 유스케이스 — AC A5-1~A5-3.
 *
 * 상태 전이 규칙은 엔티티가 갖고(§5), 이 계층은 순서를 지킨다:
 * 가시성(404 은닉) → 권한(403) → version(409) → 이름 중복(409) → 전이(409).
 * 가시성이 가장 앞인 이유는 범위 밖 프로젝트에 권한·상태를 알려 주면 존재가
 * 드러나기 때문이고, 중복 검사가 전이보다 앞인 이유는 질의를 엔티티 변경 전에
 * 끝내야 하기 때문이다(더티 상태의 질의는 flush를 유발해 version을 두 번 올린다).
 * 두 위반은 모두 409이고 아무것도 바뀌지 않는다(A5-2).
 */
@Service
@Transactional
public class ProjectEditServiceImpl implements ProjectEditService {
    private final ProjectRepository projectRepository;
    private final ProjectVisibilityService projectVisibilityService;
    private final ProjectActionPermission projectActionPermission;
    private final ProjectAuditRecorder projectAuditRecorder;
    private final ProjectViewFactory projectViewFactory;

    public ProjectEditServiceImpl(
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

    public ProjectDetail edit(long callerPersonId, EditProjectCommand command) {
        Project project =
                projectVisibilityService.requireVisible(callerPersonId, command.projectId());
        projectActionPermission.require(callerPersonId, project.getId(),
                ProjectAction.EDIT_INFO);
        project.requireVersion(command.version());

        Map<String, Object> before = projectAuditRecorder.snapshot(project);
        // 질의(중복 검사)는 엔티티를 바꾸기 **전에** 끝낸다 — 더티 상태에서 질의하면
        // JPA가 먼저 flush 하면서 version이 한 번 더 올라간다(수정 1회 = +2)
        ProjectKey key = new ProjectKey(command.client(), command.name());
        requireUniqueName(key, project.getId());
        project.advanceStatusTo(command.status());
        project.editInfo(
                key,
                command.solution(),
                command.engagement(),
                command.contractMm(),
                command.startDate(),
                command.endDate());

        Project saved = projectRepository.saveAndFlush(project);
        projectAuditRecorder.changed(callerPersonId, saved, before);

        return projectViewFactory.toDetail(saved);
    }

    /** 중복 이름 검사 (AC A1-2) — 자기 자신은 대상에서 뺀다(이름을 안 바꾸는 수정). */
    private void requireUniqueName(ProjectKey key, Long projectId) {
        boolean duplicated = projectRepository
                .existsByNormalizedClientAndNormalizedNameAndDeletedFalseAndIdNot(
                        key.normalizedClient(), key.normalizedName(), projectId);

        if (duplicated) {
            throw new ConflictException("DUPLICATE_NAME", "같은 고객사에 같은 이름의 프로젝트가 있습니다");
        }
    }
}
