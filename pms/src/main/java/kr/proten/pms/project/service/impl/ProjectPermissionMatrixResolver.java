package kr.proten.pms.project.service.impl;

import kr.proten.pms.project.repository.ProjectPermissionOverrideRepository;
import kr.proten.pms.project.service.entity.EffectiveProjectPermissions;
import kr.proten.pms.project.service.entity.ProjectAction;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.springframework.stereotype.Component;

/**
 * 저장된 override를 읽어 {@link EffectiveProjectPermissions}를 세운다 (US-A8).
 *
 * <p>병합 규칙은 이 클래스에 없다 — 그것은 값 객체가 갖고, 여기는 저장소에서
 * 한 번 읽어 넘기는 자리다. 판정 경로와 A8-1 조회가 <b>같은 병합</b>을 보게 하는 것이
 * 이 분리의 목적이다.
 */
@Component
class ProjectPermissionMatrixResolver {

    private final ProjectPermissionOverrideRepository overrideRepository;

    ProjectPermissionMatrixResolver(ProjectPermissionOverrideRepository overrideRepository) {
        this.overrideRepository = overrideRepository;
    }

    /** 그 프로젝트의 유효 매트릭스 — override를 한 번 읽는다 */
    EffectiveProjectPermissions of(long projectId) {
        return EffectiveProjectPermissions.of(overrideRepository.findByProjectId(projectId));
    }

    /** 판정 경로가 쓰는 한 칸짜리 질문 (AC A8-5·A8-6) */
    boolean allows(long projectId, ProjectRole role, ProjectAction action) {
        return of(projectId).allows(role, action);
    }
}
