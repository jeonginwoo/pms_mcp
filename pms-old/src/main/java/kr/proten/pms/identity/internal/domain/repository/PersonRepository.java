package kr.proten.pms.identity.internal.domain.repository;

import java.util.List;
import java.util.Optional;
import kr.proten.pms.identity.internal.domain.Person;

/**
 * 사람 저장소 포트 — 구현은 infra의 JPA 어댑터.
 */
public interface PersonRepository {
    Person save(Person person);

    Optional<Person> findById(Long id);

    List<Person> findAll();

    long count();
}
