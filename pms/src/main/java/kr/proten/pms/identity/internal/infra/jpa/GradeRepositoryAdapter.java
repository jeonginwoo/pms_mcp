package kr.proten.pms.identity.internal.infra.jpa;

import java.util.List;
import java.util.Optional;
import kr.proten.pms.identity.internal.domain.Grade;
import kr.proten.pms.identity.internal.domain.repository.GradeRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * GradeRepository 포트의 JPA 구현.
 */
@Repository
class GradeRepositoryAdapter implements GradeRepository {
    // Spring Data 위임 대상
    private final GradeJpaRepository jpaRepository;

    GradeRepositoryAdapter(GradeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Grade save(Grade grade) {
        return jpaRepository.save(GradeJpa.fromDomain(grade)).toDomain();
    }

    @Override
    public Optional<Grade> findById(Long id) {
        return jpaRepository.findById(id).map(GradeJpa::toDomain);
    }

    @Override
    public List<Grade> findAll() {
        return jpaRepository.findAll().stream().map(GradeJpa::toDomain).toList();
    }
}

interface GradeJpaRepository extends JpaRepository<GradeJpa, Long> {
}
