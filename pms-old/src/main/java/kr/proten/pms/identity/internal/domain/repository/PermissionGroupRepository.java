package kr.proten.pms.identity.internal.domain.repository;

import java.util.List;
import java.util.Optional;
import kr.proten.pms.identity.internal.domain.PermissionGroup;

/**
 * 권한 그룹 저장소 포트 — 구현은 infra의 JPA 어댑터.
 */
public interface PermissionGroupRepository {
    PermissionGroup save(PermissionGroup group);

    Optional<PermissionGroup> findById(Long id);

    List<PermissionGroup> findAll();
}
