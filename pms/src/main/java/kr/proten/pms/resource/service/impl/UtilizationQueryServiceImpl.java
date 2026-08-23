package kr.proten.pms.resource.service.impl;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.person.WorkforceProfile;
import kr.proten.pms.project.AssignmentDirectoryService;
import kr.proten.pms.project.MonthlyAssignment;
import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.resource.repository.CapacityRepository;
import kr.proten.pms.resource.service.UtilizationQueryService;
import kr.proten.pms.resource.service.dto.UtilizationQuery;
import kr.proten.pms.resource.service.dto.UtilizationView;
import kr.proten.pms.resource.service.entity.Capacity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가동률 조회 — EPIC C (C1-1~C1-6).
 *
 * <p>조회 시점 계산이다(저장하지 않는다 — 캐시 미도입 2026-08-06). 그래서 <b>C1-4는
 * 구현할 것이 없다</b>: 배정이 커밋되면 다음 조회가 이미 그 값을 읽는다. 재계산 이벤트를
 * 두면 두 원본이 생긴다 — 그 사실은 통합 테스트가 증명한다.
 *
 * <p>판단 분해: <b>누구를 셀지</b>는 {@link UtilizationPopulation}이, <b>과부하인지</b>는
 * {@link UtilizationView#overbooked()}가 답한다. 여기 남는 것은 분자·분모와 산식이다.
 *
 * <p><b>분자는 진행중 배정만</b>이다(2026-08-10 결정 — 완료·수주확정까지 세면 시드
 * 실측에서 1171%가 나온다). project는 상태라는 사실만 내주고 판정은 여기서 한다 —
 * 거기서 걸러 버리면 모집단 정의가 두 모듈에 나뉜다.
 */
@Service
@Transactional(readOnly = true)
class UtilizationQueryServiceImpl implements UtilizationQueryService {
    private final UtilizationPopulation population;
    private final AssignmentDirectoryService assignmentDirectory;
    private final CapacityRepository capacityRepository;

    UtilizationQueryServiceImpl(
            UtilizationPopulation population,
            AssignmentDirectoryService assignmentDirectory,
            CapacityRepository capacityRepository) {
        this.population = population;
        this.assignmentDirectory = assignmentDirectory;
        this.capacityRepository = capacityRepository;
    }

    @Override
    public List<UtilizationView> find(long callerPersonId, UtilizationQuery query) {
        List<WorkforceProfile> people = population.resolve(callerPersonId, query);

        if (people.isEmpty()) {
            // 개인 지정의 빈 결과는 404다 — 부재와 가시성 밖을 같은 답으로 덮는다.
            if (query.isSinglePerson()) {
                throw new NotFoundException();
            }

            return List.of();
        }

        Map<Long, Double> assigned = assignedMmOf(query.month(), personIdsOf(people));
        Map<Long, Double> overrides = capacityOverridesOf(query.month());

        List<UtilizationView> rows = people.stream()
                .map(profile -> viewOf(profile, query.month(), assigned, overrides))
                .filter(view -> view != null)
                .toList();

        return query.overbookedOnly()
                ? rows.stream().filter(UtilizationView::overbooked).toList()
                : rows;
    }

    /**
     * 한 사람의 한 달 가동률 — 분모가 없으면 {@code null}(행을 만들지 않는다).
     *
     * <p>가용 M/M이 0인 사람은 가동률이라는 값을 갖지 않는다. 시드에서 그 경우는 시스템
     * 계정 하나이고, 시드가 "인력·가동률·배정 목록에서 제외"라고 정해 둔 대상이다 —
     * resource는 "시스템 계정"을 알지 못하고 "분모가 없다"만 알아도 같은 결과에 이른다.
     */
    private UtilizationView viewOf(
            WorkforceProfile profile,
            YearMonth month,
            Map<Long, Double> assigned,
            Map<Long, Double> overrides) {
        // 그 달 예외가 기본값을 이긴다 — Capacity 행은 휴직·파견처럼 그 달만 다른 경우다.
        double availableMm = overrides.getOrDefault(profile.personId(), profile.defaultCapacity());

        if (availableMm <= 0) {
            return null;
        }

        double assignedMm = assigned.getOrDefault(profile.personId(), 0.0);

        return new UtilizationView(
                profile.personId(),
                profile.name(),
                profile.team(),
                profile.division(),
                month,
                assignedMm,
                availableMm,
                percentage(assignedMm, availableMm),
                // 보정은 계수를 곱한다 — 나누던 구 산식은 배정 M/M이 단가 기준일 때만
                // 성립했다(2026-08-10 재정의, 상위 PRD §3).
                percentage(assignedMm * profile.gradeCoeff(), availableMm));
    }

    /**
     * 백분율 — IEEE754 노이즈를 떼고 낸다.
     *
     * <p>M/M을 더하면 2자리 소수의 합에도 오차가 붙는다: 배정 0.5+0.7에 계수 1.5를 곱하면
     * {@code 179.99999999999997}이 나온다(실측). 응답에 그 값이 실리는 것도 문제지만,
     * <b>과부하 판정이 {@code 기본 > 100}이라 노이즈가 판정을 뒤집을 수 있다</b>는 것이
     * 이 반올림의 이유다.
     *
     * <p>6자리로 자르는 것은 <b>표시 정밀도가 아니다</b>: M/M은 소수 2자리까지라 의미 있는
     * 차이는 전부 남고 노이즈만 사라진다. 화면에 몇 자리를 쓸지는 프론트가 정한다.
     */
    private static double percentage(double assignedMm, double availableMm) {
        double raw = assignedMm / availableMm * 100.0;

        return Math.round(raw * 1_000_000.0) / 1_000_000.0;
    }

    /** 그 달 진행중 프로젝트의 배정 M/M 합 — 배정이 없는 사람은 키가 없다(0이 아니다). */
    private Map<Long, Double> assignedMmOf(YearMonth month, Set<Long> personIds) {
        return assignmentDirectory.findInMonth(month, personIds).stream()
                .filter(row -> row.projectStatus() == ProjectStatus.IN_PROGRESS)
                .collect(Collectors.groupingBy(
                        MonthlyAssignment::personId,
                        Collectors.summingDouble(MonthlyAssignment::monthlyMm)));
    }

    /** 그 달 가용 M/M 예외 — 예외가 걸린 인원만 있다(44명 × 12개월을 채우지 않는다). */
    private Map<Long, Double> capacityOverridesOf(YearMonth month) {
        return capacityRepository.findByYearMonth(month.toString()).stream()
                .collect(Collectors.toMap(Capacity::getPersonId, Capacity::getAvailableMm));
    }

    private static Set<Long> personIdsOf(List<WorkforceProfile> people) {
        return people.stream()
                .map(WorkforceProfile::personId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
