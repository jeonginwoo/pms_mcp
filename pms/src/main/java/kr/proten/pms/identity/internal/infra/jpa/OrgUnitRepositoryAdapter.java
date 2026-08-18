package kr.proten.pms.identity.internal.infra.jpa;

import java.util.List;
import java.util.Optional;
import kr.proten.pms.identity.internal.domain.OrgUnit;
import kr.proten.pms.identity.internal.domain.repository.OrgUnitRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * OrgUnitRepository 포트의 JPA 구현.
 */
@Repository
class OrgUnitRepositoryAdapter implements OrgUnitRepository {
    // Spring Data 위임 대상
    private final OrgUnitJpaRepository jpaRepository;

    OrgUnitRepositoryAdapter(OrgUnitJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public OrgUnit save(OrgUnit orgUnit) {
        return jpaRepository.save(OrgUnitJpa.fromDomain(orgUnit)).toDomain();
    }

    @Override
    public Optional<OrgUnit> findById(Long id) {
        return jpaRepository.findById(id).map(OrgUnitJpa::toDomain);
    }

    @Override
    public List<OrgUnit> findAll() {
        return jpaRepository.findAll().stream().map(OrgUnitJpa::toDomain).toList();
    }
}

interface OrgUnitJpaRepository extends JpaRepository<OrgUnitJpa, Long> {
}
