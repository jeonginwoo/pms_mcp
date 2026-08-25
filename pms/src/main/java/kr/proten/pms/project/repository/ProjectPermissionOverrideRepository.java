package kr.proten.pms.project.repository;

import java.util.List;
import kr.proten.pms.project.service.entity.ProjectPermissionOverride;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 프로젝트별 권한 커스텀 (US-A8) — 기본값과 다른 칸만 담기므로 대부분의 프로젝트는
 * 행이 0개다.
 */
public interface ProjectPermissionOverrideRepository
        extends JpaRepository<ProjectPermissionOverride, Long> {

    /**
     * 그 프로젝트의 override 전부 — 칸 단위로 묻지 않는다.
     * 병합은 8칸을 한꺼번에 세우고, 판정 한 번도 그 표를 통째로 만든다.
     */
    List<ProjectPermissionOverride> findByProjectId(Long projectId);

    /** 전체 교체 저장(A8-2)의 앞 절반 — 남길 것만 다시 넣는다 */
    void deleteByProjectId(Long projectId);
}
