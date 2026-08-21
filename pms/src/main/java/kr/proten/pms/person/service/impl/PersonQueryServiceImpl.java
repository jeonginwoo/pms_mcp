package kr.proten.pms.person.service.impl;

import kr.proten.pms.person.service.PersonQueryService;
import java.util.List;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.person.service.dto.OrgVisibility;
import kr.proten.pms.person.service.OrgVisibilityService;
import kr.proten.pms.person.service.dto.PersonRef;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인력 조회 유스케이스 — 가시성 필터와 404 은닉이 이 계층에 있다 (구조 원칙 3).
 * 목록은 가시성 내 부분집합만 돌려주고, 단건은 부재·시스템 계정·비활성·가시성
 * 밖을 같은 404로 수렴시킨다 (상위 PRD §4-4).
 */
@Service
@Transactional(readOnly = true)
public class PersonQueryServiceImpl implements PersonQueryService {
    private final OrgVisibilityService orgVisibilityService;
    private final PersonRepository personRepository;
    private final PersonRefFactory personRefFactory;

    public PersonQueryServiceImpl(
            OrgVisibilityService orgVisibilityService,
            PersonRepository personRepository,
            PersonRefFactory personRefFactory) {
        this.orgVisibilityService = orgVisibilityService;
        this.personRepository = personRepository;
        this.personRefFactory = personRefFactory;
    }

    /** 가시성 범위 내 인원 목록 — 시스템 계정·비활성 인원은 제외한다. */
    public List<PersonRef> listVisible(long callerPersonId) {
        OrgVisibility visibility = orgVisibilityService.visibilityOf(callerPersonId);

        if (visibility.unrestricted()) {
            return personRefFactory.toRefs(
                    personRepository.findByActiveTrueAndSystemFalseOrderByIdAsc());
        }

        return personRefFactory.toRefs(
                personRepository.findByIdInAndActiveTrueAndSystemFalseOrderByIdAsc(
                        visibility.visiblePersonIds()));
    }

    /**
     * 인원 단건 조회.
     * 노출 대상이 아닌 인원(부재·시스템 계정·비활성)과 가시성 밖 인원은 같은
     * 404다 — 사유가 응답으로 새면 존재 자체가 드러난다.
     */
    public PersonRef getPerson(long callerPersonId, long personId) {
        Person target = personRepository.findByIdAndActiveTrue(personId)
                .filter(person -> !person.isSystem())
                .orElseThrow(NotFoundException::new);

        if (!orgVisibilityService.visibilityOf(callerPersonId).canView(personId)) {
            throw new NotFoundException();
        }

        return personRefFactory.toRef(target);
    }
}
