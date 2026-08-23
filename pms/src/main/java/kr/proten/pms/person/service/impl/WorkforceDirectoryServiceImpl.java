package kr.proten.pms.person.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.proten.pms.person.WorkforceDirectoryService;
import kr.proten.pms.person.WorkforceProfile;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.entity.Grade;
import kr.proten.pms.person.service.entity.OrgTree;
import kr.proten.pms.person.service.entity.OrgUnit;
import kr.proten.pms.person.service.entity.Person;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link WorkforceDirectoryService} 구현 — 조직 트리를 팀·부문 두 이름으로 편다.
 *
 * <p>{@code division}은 {@link OrgTree#topDivisionIdOf}가 정하는 "소속 경로상 root
 * 직계 자식"이다. 가시성 DIVISION scope가 쓰는 것과 <b>같은 해석</b>을 쓴다 — 같은
 * 트리를 두 규칙으로 읽으면 "내 부문"이 화면과 집계에서 달라진다.
 *
 * <p>{@code team}은 소속 노드 자신의 이름이다. 부문 직속 인원은 team과 division이
 * 같은 이름이 되는데, 그 사람에게는 그것이 사실이다.
 */
@Service
@Transactional(readOnly = true)
class WorkforceDirectoryServiceImpl implements WorkforceDirectoryService {
    private final PersonRepository personRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final GradeRepository gradeRepository;

    WorkforceDirectoryServiceImpl(
            PersonRepository personRepository,
            OrgUnitRepository orgUnitRepository,
            GradeRepository gradeRepository) {
        this.personRepository = personRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.gradeRepository = gradeRepository;
    }

    @Override
    public List<WorkforceProfile> findProfiles(Collection<Long> personIds) {
        if (personIds.isEmpty()) {
            return List.of();
        }

        // 비활성 포함 — 지난달 가동률은 그때 재직 중이던 사람으로 계산된다.
        List<Person> people = personRepository.findAllById(personIds);

        if (people.isEmpty()) {
            return List.of();
        }

        List<OrgUnit> units = orgUnitRepository.findAll();
        OrgTree tree = OrgTree.of(units);
        Map<Long, String> unitNames = units.stream()
                .collect(Collectors.toMap(OrgUnit::getId, OrgUnit::getName));
        Map<Long, Double> coeffs = coeffsOf(people);

        return people.stream()
                .map(person -> new WorkforceProfile(
                        person.getId(),
                        person.getName(),
                        unitNames.get(person.getOrgUnitId()),
                        unitNames.get(tree.topDivisionIdOf(person.getOrgUnitId())),
                        person.getCapacity(),
                        person.isBillable(),
                        coeffs.getOrDefault(person.getGradeId(), 1.0)))
                .toList();
    }

    @Override
    public Set<Long> findPersonIdsInSubtree(long orgUnitId) {
        Set<Long> unitIds = OrgTree.of(orgUnitRepository.findAll()).subtreeIds(orgUnitId);

        // 재직자만 — "이 조직에 지금 누가 있나"에 퇴사자를 세면 팀 집계가 틀어진다.
        return personRepository.findByOrgUnitIdInAndActiveTrue(unitIds).stream()
                .map(Person::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 없는 직급은 계수 1.0으로 본다 — 보정 지표 하나 때문에 조회가 실패하지 않게 한다. */
    private Map<Long, Double> coeffsOf(List<Person> people) {
        Set<Long> gradeIds = people.stream()
                .map(Person::getGradeId)
                .collect(Collectors.toUnmodifiableSet());

        return gradeRepository.findAllById(gradeIds).stream()
                .collect(Collectors.toMap(Grade::getId, Grade::getCoeff));
    }
}
