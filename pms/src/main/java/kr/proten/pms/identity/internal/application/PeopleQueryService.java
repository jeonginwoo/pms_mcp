package kr.proten.pms.identity.internal.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.proten.pms.common.NotFoundException;
import kr.proten.pms.identity.internal.domain.Grade;
import kr.proten.pms.identity.internal.domain.OrgTree;
import kr.proten.pms.identity.internal.domain.OrgUnit;
import kr.proten.pms.identity.internal.domain.Person;
import kr.proten.pms.identity.internal.domain.PersonVisibility;
import kr.proten.pms.identity.internal.domain.repository.GradeRepository;
import kr.proten.pms.identity.internal.domain.repository.OrgUnitRepository;
import kr.proten.pms.identity.internal.domain.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인력 조회 유스케이스 — 가시성 필터와 404 은닉이 이 계층에 있다 (구조 원칙 3).
 * 목록은 가시성 내 부분집합만 돌려주고, 단건은 부재·가시성 밖을 같은 404로
 * 수렴시킨다 (상위 PRD §4-4). 시스템 계정·비활성 인원은 목록·단건 모두에서
 * 제외한다 (2026-08-09 ④ · E2-3).
 * ASSUMPTION: 44명 규모라 전체 로드 후 메모리 필터 — 인원이 커지면 질의 하향.
 */
@Service
@Transactional(readOnly = true)
public class PeopleQueryService {
    // 요청자 컨텍스트 해석
    private final RequesterResolver requesterResolver;
    // 인원 조회
    private final PersonRepository personRepository;
    // 가시성 subtree 계산·조직명 매핑
    private final OrgUnitRepository orgUnitRepository;
    // 직급명 매핑
    private final GradeRepository gradeRepository;

    public PeopleQueryService(
            RequesterResolver requesterResolver,
            PersonRepository personRepository,
            OrgUnitRepository orgUnitRepository,
            GradeRepository gradeRepository) {
        this.requesterResolver = requesterResolver;
        this.personRepository = personRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.gradeRepository = gradeRepository;
    }

    /** 인력 응답 — 목록·단건 공용. 상세 확장(참여 프로젝트·가동률)은 PMS-M2·M3. */
    public record PersonSummary(Long id, String name, String orgUnit, String grade) {
    }

    /** 가시성 범위 내 인원 목록. */
    public List<PersonSummary> listVisible(Long callerPersonId) {
        PersonVisibility visibility = visibilityOf(requesterResolver.resolve(callerPersonId));
        Map<Long, String> orgUnitNames = orgUnitRepository.findAll().stream()
                .collect(Collectors.toMap(OrgUnit::id, OrgUnit::name));
        Map<Long, String> gradeNames = gradeRepository.findAll().stream()
                .collect(Collectors.toMap(Grade::id, Grade::name));

        return personRepository.findAll().stream()
                .filter(Person::active)
                .filter(person -> !person.system())
                .filter(visibility::canView)
                .sorted(Comparator.comparing(Person::id))
                .map(person -> toSummary(person, orgUnitNames::get, gradeNames::get))
                .toList();
    }

    /** 단건 조회 — 부재·시스템 계정·비활성·가시성 밖 전부 같은 404 (은닉 동형). */
    public PersonSummary getPerson(Long callerPersonId, Long personId) {
        Requester requester = requesterResolver.resolve(callerPersonId);
        Person target = personRepository.findById(personId)
                .filter(Person::active)
                .filter(person -> !person.system())
                .orElseThrow(NotFoundException::new);

        if (!visibilityOf(requester).canView(target)) {
            throw new NotFoundException();
        }

        return toSummary(
                target,
                orgUnitId -> orgUnitRepository.findById(orgUnitId).map(OrgUnit::name).orElse(null),
                gradeId -> gradeRepository.findById(gradeId).map(Grade::name).orElse(null));
    }

    private PersonVisibility visibilityOf(Requester requester) {
        OrgTree tree = OrgTree.of(orgUnitRepository.findAll());

        return PersonVisibility.of(requester.person(), requester.group(), tree);
    }

    private PersonSummary toSummary(
            Person person,
            Function<Long, String> orgUnitName,
            Function<Long, String> gradeName) {
        return new PersonSummary(
                person.id(),
                person.name(),
                orgUnitName.apply(person.orgUnitId()),
                gradeName.apply(person.gradeId()));
    }
}
