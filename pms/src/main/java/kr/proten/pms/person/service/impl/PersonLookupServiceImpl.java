package kr.proten.pms.person.service.impl;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.proten.pms.person.PersonIdentity;
import kr.proten.pms.person.PersonLookupService;
import kr.proten.pms.person.PersonRef;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.service.PersonService;
import kr.proten.pms.person.service.dto.MeView;
import kr.proten.pms.person.service.entity.OrgTree;
import kr.proten.pms.person.service.entity.OrgUnit;
import kr.proten.pms.person.service.impl.requester.Requester;
import kr.proten.pms.person.service.impl.requester.RequesterResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모듈 밖 인원 조회 구현 — 신원 해석·가시성 판정을 `PersonService`에 위임하고
 * 부문 이름만 덧붙인다.
 *
 * 판정을 직접 하지 않고 `PersonService`를 부르는 것이 이 클래스의 규칙이다:
 * 챗(`/mcp`)과 화면(`/api/me`·`/api/people`)이 다른 답을 내면 "챗에서 보이는 것은
 * 화면에서 보이는 것과 같다"는 성질이 깨지고, 그 성질은 가시성 판정이 한 곳에 있을
 * 때만 유지된다(상위 PRD §4-4). 그래서 여기서 하는 일은 **표현 변환**뿐이다 —
 * 화면용 `MeView`의 권한 플래그를 떨어뜨리고 부문을 채운다.
 */
@Service
@Transactional(readOnly = true)
public class PersonLookupServiceImpl implements PersonLookupService {
    private final PersonService personService;
    private final RequesterResolver requesterResolver;
    private final OrgUnitRepository orgUnitRepository;

    public PersonLookupServiceImpl(
            PersonService personService,
            RequesterResolver requesterResolver,
            OrgUnitRepository orgUnitRepository) {
        this.personService = personService;
        this.requesterResolver = requesterResolver;
        this.orgUnitRepository = orgUnitRepository;
    }

    @Override
    public PersonIdentity identityOf(long callerPersonId) {
        // 신원·권한 그룹명 해석은 /api/me와 같은 경로를 쓴다(PersonService.me 주석)
        MeView me = personService.me(callerPersonId);
        Requester requester = requesterResolver.resolve(callerPersonId);

        return new PersonIdentity(
                me.id(),
                me.name(),
                me.orgUnit(),
                divisionNameOf(requester.person().getOrgUnitId()),
                me.group());
    }

    @Override
    public List<PersonRef> search(long callerPersonId, String name, String team) {
        return personService.listVisible(callerPersonId).stream()
                .filter(person -> matches(person.name(), name))
                .filter(person -> matches(person.orgUnit(), team))
                .toList();
    }

    /**
     * 소속 경로상 최상위 부문 이름. 부문 직속 소속이면 그 부문 자신이 나오므로
     * team과 같은 값이 된다 — {@link OrgTree#topDivisionIdOf}의 규약을 그대로 따른다.
     */
    private String divisionNameOf(Long orgUnitId) {
        Map<Long, OrgUnit> unitsById = orgUnitRepository.findAll().stream()
                .collect(Collectors.toMap(OrgUnit::getId, Function.identity()));
        Long divisionId = OrgTree.of(List.copyOf(unitsById.values())).topDivisionIdOf(orgUnitId);

        return unitsById.get(divisionId).getName();
    }

    /** 미지정(null·공백)은 필터 없음, 지정되면 부분 일치. 검색어의 겉 공백은 버린다. */
    private boolean matches(String value, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }

        return value != null && value.contains(keyword.trim());
    }
}
