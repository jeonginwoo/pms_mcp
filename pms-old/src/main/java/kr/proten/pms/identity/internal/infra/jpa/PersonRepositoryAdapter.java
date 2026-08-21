package kr.proten.pms.identity.internal.infra.jpa;

import java.util.List;
import java.util.Optional;
import kr.proten.pms.identity.internal.domain.Person;
import kr.proten.pms.identity.internal.domain.repository.PersonRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * PersonRepository 포트의 JPA 구현.
 */
@Repository
class PersonRepositoryAdapter implements PersonRepository {
    // Spring Data 위임 대상
    private final PersonJpaRepository jpaRepository;

    PersonRepositoryAdapter(PersonJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Person save(Person person) {
        return jpaRepository.save(PersonJpa.fromDomain(person)).toDomain();
    }

    @Override
    public Optional<Person> findById(Long id) {
        return jpaRepository.findById(id).map(PersonJpa::toDomain);
    }

    @Override
    public List<Person> findAll() {
        return jpaRepository.findAll().stream().map(PersonJpa::toDomain).toList();
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }
}

interface PersonJpaRepository extends JpaRepository<PersonJpa, Long> {
}
