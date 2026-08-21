package kr.proten.pms.identity.internal.infra.jpa;

import java.util.Optional;
import kr.proten.pms.identity.internal.domain.User;
import kr.proten.pms.identity.internal.domain.repository.UserRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * UserRepository 포트의 JPA 구현.
 */
@Repository
class UserRepositoryAdapter implements UserRepository {
    // Spring Data 위임 대상
    private final UserJpaRepository jpaRepository;

    UserRepositoryAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public User save(User user) {
        return jpaRepository.save(UserJpa.fromDomain(user)).toDomain();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(UserJpa::toDomain);
    }

    @Override
    public Optional<User> findByPersonId(Long personId) {
        return jpaRepository.findByPersonId(personId).map(UserJpa::toDomain);
    }
}

interface UserJpaRepository extends JpaRepository<UserJpa, Long> {
    Optional<UserJpa> findByEmail(String email);

    Optional<UserJpa> findByPersonId(Long personId);
}
