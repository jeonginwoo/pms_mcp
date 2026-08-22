package kr.proten.pms.person.service.impl;

import java.util.Collection;
import java.util.List;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.person.PersonRef;
import kr.proten.pms.person.repository.PersonRepository;
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

        return personRefFactory.toRefs(
                personRepository.findByIdInAndActiveTrue(personIds.stream().sorted().toList()));
    }
}
