package kr.proten.pms.resource.service.impl;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.person.WorkforceProfile;
import kr.proten.pms.project.AssignmentDirectoryService;
import kr.proten.pms.project.MonthlyAssignment;
import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.resource.repository.CapacityRepository;
import kr.proten.pms.resource.service.dto.UtilizationQuery;
import kr.proten.pms.resource.service.entity.Capacity;
import org.springframework.stereotype.Component;

/**
 * 가동률 한 달치를 읽는 일 전체 — EPIC C의 정본이다 (AC C1-1~C1-6).
 *
 * <p>유스케이스가 아니라 협력자인 이유: <b>호출자가 둘</b>이다. 웹({@code UtilizationQueryService})과
 * {@code /mcp} 어댑터({@code UtilizationLookupService})가 같은 수치를 서로 다른 모양으로
 * 답한다. 둘 중 하나에 본문을 두고 다른 하나가 그것을 부르게 하면 출력 모양이 호출
 * 방향을 정하게 되고, 각자 계산하게 하면 산식·모집단·과부하 판정이 두 벌이 된다
 * (2026-08-24 결정 기록 ③).
 *
 * <p>여기 있는 것과 없는 것: <b>누구를 셀지</b>는 {@link UtilizationPopulation}이,
 * <b>과부하인지</b>는 {@link PersonUtilization#overbooked()}가 답한다. 여기 남는 것은
 * 분자·분모와 산식, 그리고 <b>빈 결과의 의미</b>다.
 *
 * <p>빈 결과의 의미를 두 호출자에게 넘기지 않는 이유: 개인 지정의 빈 결과는 404이고
 * 집계의 빈 결과는 빈 목록인데, 그 갈림을 각 서비스가 따로 가지면 챗과 화면에서
 * 은닉 규칙이 어긋날 수 있다. 은닉은 보안 의미를 갖는 동작이라 한 곳에 둔다.
 *
 * <p>조회 시점 계산이다(저장하지 않는다 — 캐시 미도입 2026-08-06). 그래서 <b>C1-4는
 * 구현할 것이 없다</b>: 배정이 커밋되면 다음 조회가 이미 그 값을 읽는다.
 *
 * <p><b>분자는 진행중 배정만</b>이다(2026-08-10 결정 — 완료·수주확정까지 세면 시드
 * 실측에서 1171%가 나온다). project는 상태라는 사실만 내주고 판정은 여기서 한다 —
 * 거기서 걸러 버리면 모집단 정의가 두 모듈에 나뉜다.
 */
@Component
class UtilizationCalculator {
    private final UtilizationPopulation population;
    private final AssignmentDirectoryService assignmentDirectory;
    private final CapacityRepository capacityRepository;

    UtilizationCalculator(
            UtilizationPopulation population,
            AssignmentDirectoryService assignmentDirectory,
            CapacityRepository capacityRepository) {
        this.population = population;
        this.assignmentDirectory = assignmentDirectory;
        this.capacityRepository = capacityRepository;
    }

    /**
     * 조건에 맞는 가동률 행 — 프로젝트별 기여분까지 담긴 채로 돌려준다.
     *
     * @throws NotFoundException 개인 지정인데 대상이 없거나 가시성 밖일 때 (은닉)
     */
    List<PersonUtilization> calculate(long callerPersonId, UtilizationQuery query) {
        List<WorkforceProfile> people = population.resolve(callerPersonId, query);

        if (people.isEmpty()) {
            // 개인 지정의 빈 결과는 404다 — 부재와 가시성 밖을 같은 답으로 덮는다.
            if (query.isSinglePerson()) {
                throw new NotFoundException();
            }

            return List.of();
        }

        Map<Long, List<MonthlyAssignment>> assigned =
                inProgressAssignmentsOf(query.month(), personIdsOf(people));
        Map<Long, Double> overrides = capacityOverridesOf(query.month());

        List<PersonUtilization> rows = people.stream()
                .map(profile -> rowOf(
                        profile,
                        query.month(),
                        assigned.getOrDefault(profile.personId(), List.of()),
                        overrides))
                .filter(Objects::nonNull)
                .toList();

        return query.overbookedOnly()
                ? rows.stream().filter(PersonUtilization::overbooked).toList()
                : rows;
    }

    /**
     * 한 사람의 한 달 가동률 — 분모가 없으면 {@code null}(행을 만들지 않는다).
     *
     * <p>가용 M/M이 0인 사람은 가동률이라는 값을 갖지 않는다. 시드에서 그 경우는 시스템
     * 계정 하나이고, 시드가 "인력·가동률·배정 목록에서 제외"라고 정해 둔 대상이다 —
     * resource는 "시스템 계정"을 알지 못하고 "분모가 없다"만 알아도 같은 결과에 이른다.
     */
    private static PersonUtilization rowOf(
            WorkforceProfile profile,
            YearMonth month,
            List<MonthlyAssignment> assignments,
            Map<Long, Double> overrides) {
        // 그 달 예외가 기본값을 이긴다 — Capacity 행은 휴직·파견처럼 그 달만 다른 경우다.
        double availableMm = overrides.getOrDefault(profile.personId(), profile.defaultCapacity());

        if (availableMm <= 0) {
            return null;
        }

        double assignedMm = assignments.stream()
                .mapToDouble(MonthlyAssignment::monthlyMm)
                .sum();

        return new PersonUtilization(
                profile,
                month,
                // 합은 반올림하지 않는다 — 반올림의 이유(노이즈가 과부하 판정을 뒤집는 것)는
                // 백분율에만 걸린다. 웹 응답의 기존 값을 그대로 유지한다.
                assignedMm,
                availableMm,
                percentage(assignedMm, availableMm),
                // 보정은 계수를 곱한다 — 나누던 구 산식은 배정 M/M이 단가 기준일 때만
                // 성립했다(2026-08-10 재정의, 상위 PRD §3).
                percentage(assignedMm * profile.gradeCoeff(), availableMm),
                sharesOf(assignments));
    }

    /**
     * 프로젝트별 기여분 — 같은 프로젝트에 배정 행이 둘 이상이면 합쳐서 한 줄로 낸다.
     *
     * <p>합치는 이유: 원인 목록은 사람이 읽는 것이고, 같은 프로젝트 이름이 두 번 나오면
     * 읽는 쪽이 그것을 다시 더한다. 큰 것부터 놓는 것도 같은 이유다 — 과부하의 원인을
     * 물었을 때 첫 줄이 가장 큰 원인이어야 한다.
     *
     * <p><b>0 M/M 배정은 원인이 아니다</b>(2026-08-24 실측 — 시드 이현창의 롯데관광 배정).
     * 실무자가 따로 있는 프로젝트의 PM 배정은 M/M이 0이고, 그것은 규칙이 그렇게 정한
     * 것이다 — "체크 역할은 부하 없음"(부록 B ③ · A6-7 기본값). 목록에 남기면 부하에
     * 기여하지 않은 프로젝트를 과부하의 원인으로 내놓는 셈이라 답이 틀린다. 분자에는
     * 어차피 0을 더하므로 <b>합계는 그대로</b>다.
     */
    private static List<PersonUtilization.ProjectShare> sharesOf(List<MonthlyAssignment> assignments) {
        return assignments.stream()
                .collect(Collectors.groupingBy(
                        MonthlyAssignment::projectName,
                        Collectors.summingDouble(MonthlyAssignment::monthlyMm)))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new PersonUtilization.ProjectShare(entry.getKey(), round(entry.getValue())))
                .sorted((left, right) -> Double.compare(right.mm(), left.mm()))
                .toList();
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
        return round(assignedMm / availableMm * 100.0);
    }

    private static double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }

    /** 그 달 진행중 프로젝트의 배정 — 배정이 없는 사람은 키가 없다(빈 목록이 아니다). */
    private Map<Long, List<MonthlyAssignment>> inProgressAssignmentsOf(
            YearMonth month, Set<Long> personIds) {
        return assignmentDirectory.findInMonth(month, personIds).stream()
                .filter(row -> row.projectStatus() == ProjectStatus.IN_PROGRESS)
                .collect(Collectors.groupingBy(MonthlyAssignment::personId));
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
