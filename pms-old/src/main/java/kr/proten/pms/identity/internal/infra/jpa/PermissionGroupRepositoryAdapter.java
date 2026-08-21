package kr.proten.pms.identity.internal.infra.jpa;

import java.util.List;
import java.util.Optional;
import kr.proten.pms.identity.internal.domain.PermissionGroup;
import kr.proten.pms.identity.internal.domain.repository.PermissionGroupRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * PermissionGroupRepository 포트의 JPA 구현.
 */
@Repository
class PermissionGroupRepositoryAdapter implements PermissionGroupRepository {
    // Spring Data 위임 대상
    private final PermissionGroupJpaRepository jpaRepository;

    PermissionGroupRepositoryAdapter(PermissionGroupJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PermissionGroup save(PermissionGroup group) {
        return jpaRepository.save(PermissionGroupJpa.fromDomain(group)).toDomain();
    }

    @Override
    public Optional<PermissionGroup> findById(Long id) {
        return jpaRepository.findById(id).map(PermissionGroupJpa::toDomain);
    }

    @Override
    public List<PermissionGroup> findAll() {
        return jpaRepository.findAll().stream().map(PermissionGroupJpa::toDomain).toList();
    }
}

interface PermissionGroupJpaRepository extends JpaRepository<PermissionGroupJpa, Long> {
}
