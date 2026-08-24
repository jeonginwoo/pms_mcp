package kr.proten.pms.person.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.person.PersonRef;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.entity.Person;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인원 참조 조회 포트 구현 — 다른 모듈이 id로 인원을 확인·표시하는 경로.
 * 가시성 판정을 하지 않는다: 참조 검증(존재하는 인원인가)과 가시성(내가 볼 수
 * 있는가)은 다른 질문이며, 가시성은 호출 측 유스케이스가 자기 맥락에서 판정한다.
 */
@Service
@Transactional(readOnly = true)
public class PersonDirectoryServiceImpl implements PersonDirectoryService {
    private final PersonRepository personRepository;
    private final PersonRefFactory personRefFactory;

    public PersonDirectoryServiceImpl(PersonRepository personRepository, PersonRefFactory personRefFactory) {
        this.personRepository = personRepository;
        this.personRefFactory = personRefFactory;
    }

    @Override
    public boolean existsActive(long personId) {
        return personRepository.existsByIdAndActiveTrue(personId);
    }

    @Override
    public List<PersonRef> findRefs(Collection<Long> personIds) {
        if (personIds.isEmpty()) {
            return List.of();
        }

        // 퇴사자도 낸다 — 이유는 계약 javadoc에 있다(표시 이름 ≠ 참조 검증)
        return personRefFactory.toRefs(
                personRepository.findByIdInOrderByIdAsc(personIds.stream().sorted().toList()));
    }

    @Override
    public Optional<Long> findIdByExactName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        List<Person> found = personRepository.findByNameAndActiveTrue(name.trim());

        // 동명이인이면 이름은 식별자가 아니다 — 하나를 골라 주면 조용히 틀린다
        return found.size() == 1 ? Optional.of(found.getFirst().getId()) : Optional.empty();
    }
}
