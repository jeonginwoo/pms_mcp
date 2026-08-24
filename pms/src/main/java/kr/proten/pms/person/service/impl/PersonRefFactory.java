package kr.proten.pms.person.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import kr.proten.pms.person.PersonRef;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.service.dto.PersonSummary;
import kr.proten.pms.person.service.entity.Grade;
import kr.proten.pms.person.service.entity.OrgTree;
import kr.proten.pms.person.service.entity.OrgUnit;
import kr.proten.pms.person.service.entity.Person;
import org.springframework.stereotype.Component;

/**
 * 인원 엔티티 → {@link PersonRef} 변환 (조직명·직급명 해석 포함).
 * 인력 조회와 인원 참조 조회가 같은 표현을 쓰므로 변환을 한 곳에 둔다 — 이름
 * 해석을 두 서비스가 각자 하면 표시 규칙이 갈라진다.
 */
@Component
class PersonRefFactory {
    private final OrgUnitRepository orgUnitRepository;
    private final GradeRepository gradeRepository;

    PersonRefFactory(OrgUnitRepository orgUnitRepository, GradeRepository gradeRepository) {
        this.orgUnitRepository = orgUnitRepository;
        this.gradeRepository = gradeRepository;
    }

    List<PersonRef> toRefs(List<Person> people) {
        if (people.isEmpty()) {
            return List.of();
        }

        List<OrgUnit> units = orgUnitRepository.findAll();
        Map<Long, String> orgUnitNames = units.stream()
                .collect(Collectors.toMap(OrgUnit::getId, OrgUnit::getName));
        // 부문은 가시성 DIVISION scope와 같은 해석으로 읽는다(root 직계 자식)
        OrgTree tree = OrgTree.of(units);
        Map<Long, String> gradeNames = gradeNamesOf(people);

        return people.stream()
                .map(person -> new PersonRef(
                        person.getId(),
                        person.getName(),
                        orgUnitNames.get(person.getOrgUnitId()),
                        orgUnitNames.get(tree.topDivisionIdOf(person.getOrgUnitId())),
                        gradeNames.get(person.getGradeId())))
                .toList();
    }

    PersonRef toRef(Person person) {
        return toRefs(List.of(person)).getFirst();
    }

    /**
     * 화면용 인원 행 — 표시 이름은 {@link #toRefs}가 이미 푼 것을 그대로 쓰고
     * 편집용 id·version만 얹는다. 이름 해석이 두 벌이 되지 않게 하는 것이 이
     * 클래스의 목적이므로, 요약도 참조를 거쳐 만든다.
     */
    List<PersonSummary> toSummaries(List<Person> people) {
        List<PersonRef> refs = toRefs(people);

        return IntStream.range(0, people.size())
                .mapToObj(index -> summaryOf(people.get(index), refs.get(index)))
                .toList();
    }

    PersonSummary toSummary(Person person) {
        return toSummaries(List.of(person)).getFirst();
    }

    private static PersonSummary summaryOf(Person person, PersonRef ref) {
        return new PersonSummary(
                ref.id(),
                ref.name(),
                ref.orgUnit(),
                ref.division(),
                ref.grade(),
                person.getOrgUnitId(),
                person.getGradeId(),
                person.getGroupId(),
                person.getVersion());
    }

    private Map<Long, String> gradeNamesOf(List<Person> people) {
        Set<Long> gradeIds = people.stream()
                .map(Person::getGradeId)
                .collect(Collectors.toUnmodifiableSet());

        return gradeRepository.findAllById(gradeIds).stream()
                .collect(Collectors.toMap(Grade::getId, Grade::getName));
    }
}
