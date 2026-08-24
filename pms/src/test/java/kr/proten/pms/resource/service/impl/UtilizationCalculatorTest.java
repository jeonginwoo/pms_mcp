package kr.proten.pms.resource.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.person.WorkforceProfile;
import kr.proten.pms.project.AssignmentDirectoryService;
import kr.proten.pms.project.MonthlyAssignment;
import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.resource.repository.CapacityRepository;
import kr.proten.pms.resource.service.dto.UtilizationQuery;
import kr.proten.pms.resource.service.entity.Capacity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 가동률 산식 단위 테스트 (AC C1-1·C1-2·C1-3).
 *
 * <p>모집단 판정은 {@link UtilizationPopulation}의 몫이라 목으로 세워 두고, 여기서는
 * <b>얼마인가</b>만 본다 — 분자(진행중 배정 합), 분모(그 달 예외 우선), 두 산식,
 * 과부하 필터, 그리고 프로젝트별 기여분.
 *
 * <p>C1-2의 고정값(0.5+0.7 · 가용 1.0 · coeff 1.2 → 기본 120 · 보정 144)이 이 테스트의
 * 앵커다. 2026-08-10 재정의로 보정은 계수를 <b>곱한다</b> — 나누던 구 산식이면 100이
 * 나오므로 이 한 케이스가 산식 방향을 고정한다.
 *
 * <p>2026-08-24에 {@code UtilizationQueryServiceImpl}에서 이 클래스로 옮겨진 테스트다:
 * 산식이 웹 유스케이스를 떠나 두 호출자(웹·{@code /mcp})의 공통 협력자가 됐다. 케이스는
 * 그대로이고 검사 대상만 {@code UtilizationView} → {@link PersonUtilization}으로 바뀌었다.
 */
@ExtendWith(MockitoExtension.class)
class UtilizationCalculatorTest {
    private static final YearMonth MONTH = YearMonth.of(2026, 8);
    private static final long CALLER_ID = 102L;
    private static final long PERSON_ID = 103L;
    /**
     * 원인의 합과 총합을 견주는 허용오차.
     *
     * <p><b>정확히 같기를 요구할 수 없다</b>(2026-08-24 실측): 두 값은 이미 각각 6자리로
     * 잘려 있지만, 잘린 원인들을 <b>다시 더하면</b> {@code DoubleStream.sum()}의 보정
     * 합산이 1 ULP를 되살린다 — 0.88+0.75+0.28이 순진한 덧셈으로는 1.91인데 스트림 합은
     * 1.9100000000000001이다(이 노이즈의 진짜 출처가 그것이다). 소비자가 합을 다시 낼
     * 자유가 있는 한 비트 일치는 성립하지 않고, M/M은 소수 2자리 값이라 이 오차는
     * 의미를 갖지 않는다.
     */
    private static final Offset<Double> MM_TOLERANCE = Offset.offset(1e-6);

    @Mock
    private UtilizationPopulation population;
    @Mock
    private AssignmentDirectoryService assignmentDirectory;
    @Mock
    private CapacityRepository capacityRepository;
    @InjectMocks
    private UtilizationCalculator calculator;

    @Test
    @DisplayName("C1-2 — A 0.5 + B 0.7, 가용 1.0, coeff 1.2 → 기본 120 · 보정 144")
    void appliesFormulaToFixedExample() {
        // Given
        givenPopulation(profile(PERSON_ID, 1.0, 1.2));
        givenAssignments(
                assignment(PERSON_ID, 1L, "A", ProjectStatus.IN_PROGRESS, 0.5),
                assignment(PERSON_ID, 2L, "B", ProjectStatus.IN_PROGRESS, 0.7));

        // When
        List<PersonUtilization> found = calculator.calculate(CALLER_ID, query(null, null, false));

        // Then: 보정은 계수를 곱한다 — 나누면 100이 나온다(2026-08-10 재정의)
        assertThat(found).singleElement().satisfies(row -> {
            assertThat(row.assignedMm()).isEqualTo(1.2);
            assertThat(row.availableMm()).isEqualTo(1.0);
            assertThat(row.basicPct()).isEqualTo(120.0);
            assertThat(row.adjustedPct()).isEqualTo(144.0);
        });
    }

    @Test
    @DisplayName("C1-1 — 진행중이 아닌 프로젝트의 배정은 분자에서 빠진다")
    void countsInProgressAssignmentsOnly() {
        // Given: 완료·수주확정까지 세면 시드 실측에서 1171%가 나온다(2026-08-10 결정)
        givenPopulation(profile(PERSON_ID, 1.0, 1.0));
        givenAssignments(
                assignment(PERSON_ID, 1L, "진행중", ProjectStatus.IN_PROGRESS, 0.5),
                assignment(PERSON_ID, 2L, "완료", ProjectStatus.COMPLETED, 0.9),
                assignment(PERSON_ID, 3L, "수주확정", ProjectStatus.ORDER_CONFIRMED, 0.9),
                assignment(PERSON_ID, 4L, "유지보수중", ProjectStatus.UNDER_MAINTENANCE, 0.9));

        // When
        List<PersonUtilization> found = calculator.calculate(CALLER_ID, query(null, null, false));

        // Then: 모집단 판정은 resource의 것이다 — project는 상태라는 사실만 내준다
        assertThat(found).singleElement().satisfies(row -> {
            assertThat(row.basicPct()).isEqualTo(50.0);
            // 원인 목록도 같은 필터를 탄다 — 진행중 아닌 3건이 "왜 바쁜가"에 섞이지 않는다
            assertThat(row.shares())
                    .extracting(PersonUtilization.ProjectShare::projectName)
                    .containsExactly("진행중");
        });
    }

    @Test
    @DisplayName("분모 — 그 달 Capacity 행이 있으면 Person 기본값을 이긴다")
    void monthlyCapacityOverridesDefault() {
        // Given: 휴직·파견처럼 그 달만 다른 경우
        givenPopulation(profile(PERSON_ID, 1.0, 1.0));
        givenAssignments(assignment(PERSON_ID, 1L, "A", ProjectStatus.IN_PROGRESS, 0.5));
        when(capacityRepository.findByYearMonth(MONTH.toString()))
                .thenReturn(List.of(Capacity.of(PERSON_ID, MONTH, 0.5)));

        // When
        List<PersonUtilization> found = calculator.calculate(CALLER_ID, query(null, null, false));

        // Then: 0.5 / 0.5 = 100 (기본값 1.0을 썼다면 50이다)
        assertThat(found).singleElement().satisfies(row -> {
            assertThat(row.availableMm()).isEqualTo(0.5);
            assertThat(row.basicPct()).isEqualTo(100.0);
        });
    }

    @Test
    @DisplayName("배정이 없는 사람도 0%로 목록에 남는다 — 집계는 명단이 온전해야 한다")
    void keepsUnassignedPeopleAtZero() {
        givenPopulation(profile(PERSON_ID, 1.0, 1.0));
        givenAssignments();

        assertThat(calculator.calculate(CALLER_ID, query(null, null, false)))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.assignedMm()).isZero();
                    assertThat(row.basicPct()).isZero();
                    assertThat(row.shares()).isEmpty();
                });
    }

    @Test
    @DisplayName("M/M 합의 부동소수점 노이즈가 응답에 새지 않는다 — 과부하 판정이 여기 걸린다")
    void stripsFloatingPointNoise() {
        // Given: 0.5 + 0.7 에 계수 1.5를 곱하면 보정이 179.99999999999997 이 된다(실측)
        givenPopulation(profile(PERSON_ID, 1.0, 1.5));
        givenAssignments(
                assignment(PERSON_ID, 1L, "A", ProjectStatus.IN_PROGRESS, 0.5),
                assignment(PERSON_ID, 2L, "B", ProjectStatus.IN_PROGRESS, 0.7));

        // When
        List<PersonUtilization> found = calculator.calculate(CALLER_ID, query(null, null, false));

        // Then: 판정이 노이즈로 갈리면 같은 데이터가 실행마다 다른 답을 낸다
        assertThat(found).singleElement().satisfies(row -> {
            assertThat(row.basicPct()).isEqualTo(120.0);
            assertThat(row.adjustedPct()).isEqualTo(180.0);
        });
    }

    @Test
    @DisplayName("배정 M/M 합도 잘린다 — 모델이 읽는 값이고 원인의 합과 맞아야 한다")
    void stripsNoiseFromAssignedSum() {
        // Given: 시드 이현창의 2026-08 실측 조합이다 — 합이 1.9100000000000001이 된다
        givenPopulation(profile(PERSON_ID, 1.0, 1.0));
        givenAssignments(
                assignment(PERSON_ID, 1L, "A", ProjectStatus.IN_PROGRESS, 0.88),
                assignment(PERSON_ID, 2L, "B", ProjectStatus.IN_PROGRESS, 0.75),
                assignment(PERSON_ID, 3L, "C", ProjectStatus.IN_PROGRESS, 0.28));

        // When
        List<PersonUtilization> found = calculator.calculate(CALLER_ID, query(null, null, false));

        // Then: 응답에 실리는 값이 1.91이다 — 자르지 않으면 1.9100000000000001이 나간다
        assertThat(found).singleElement().satisfies(row -> {
            assertThat(row.assignedMm()).isEqualTo(1.91);
            assertThat(sumOf(row)).isCloseTo(row.assignedMm(), MM_TOLERANCE);
        });
    }

    @Test
    @DisplayName("가용 M/M이 0이면 행을 만들지 않는다 — 분모가 없는 가동률은 값이 아니다")
    void dropsRowsWithoutDenominator() {
        // Given: 시드에서 capacity 0인 사람은 시스템 계정 하나이고, 시드가
        //        "인력·가동률·배정 목록에서 제외"라고 정해 둔 대상이다
        givenPopulation(profile(PERSON_ID, 0.0, 1.0), profile(200L, 1.0, 1.0));
        givenAssignments(assignment(200L, 1L, "A", ProjectStatus.IN_PROGRESS, 0.5));

        // When
        List<PersonUtilization> found = calculator.calculate(CALLER_ID, query(null, null, false));

        // Then: resource는 "시스템 계정"을 알지 못하고 "분모가 없다"만 안다
        assertThat(found).extracting(row -> row.profile().personId()).containsExactly(200L);
    }

    @Test
    @DisplayName("C1-3 — overbooked 필터는 기본 가동률로 판정한다 (보정이 아니다)")
    void overbookedFilterJudgesOnBasic() {
        // Given: 기본 100 · 보정 120 — 구 규칙("보정>100")이면 걸리는 사람
        givenPopulation(profile(PERSON_ID, 1.0, 1.2), profile(200L, 1.0, 1.0));
        givenAssignments(
                assignment(PERSON_ID, 1L, "A", ProjectStatus.IN_PROGRESS, 1.0),
                assignment(200L, 2L, "B", ProjectStatus.IN_PROGRESS, 1.5));

        // When
        List<PersonUtilization> found = calculator.calculate(CALLER_ID, query(null, null, true));

        // Then: 기본이 100을 넘는 200번만 남는다 (2026-08-10 — 구 "보정>100" 대체)
        assertThat(found).extracting(row -> row.profile().personId()).containsExactly(200L);
    }

    @Test
    @DisplayName("원인 목록 — 프로젝트별로 갈리고, 큰 것부터, 합은 배정 합과 같다")
    void sharesExplainTheNumerator() {
        // Given: 원인 목록만으로 "왜 과부하인가"가 설명돼야 한다(list_overbooked)
        givenPopulation(profile(PERSON_ID, 1.0, 1.0));
        givenAssignments(
                assignment(PERSON_ID, 1L, "작은 것", ProjectStatus.IN_PROGRESS, 0.2),
                assignment(PERSON_ID, 2L, "큰 것", ProjectStatus.IN_PROGRESS, 0.9));

        // When
        List<PersonUtilization> found = calculator.calculate(CALLER_ID, query(null, null, false));

        // Then
        assertThat(found).singleElement().satisfies(row -> {
            assertThat(row.shares())
                    .extracting(PersonUtilization.ProjectShare::projectName)
                    .containsExactly("큰 것", "작은 것");
            assertThat(sumOf(row)).isCloseTo(row.assignedMm(), MM_TOLERANCE);
        });
    }

    @Test
    @DisplayName("같은 프로젝트의 배정 행이 둘이면 원인은 한 줄로 합쳐진다")
    void mergesSharesOfTheSameProject() {
        // Given: 읽는 쪽이 같은 이름을 다시 더하게 만들지 않는다
        givenPopulation(profile(PERSON_ID, 1.0, 1.0));
        givenAssignments(
                assignment(PERSON_ID, 7L, "한 프로젝트", ProjectStatus.IN_PROGRESS, 0.3),
                assignment(PERSON_ID, 7L, "한 프로젝트", ProjectStatus.IN_PROGRESS, 0.4));

        // When
        List<PersonUtilization> found = calculator.calculate(CALLER_ID, query(null, null, false));

        // Then
        assertThat(found).singleElement().satisfies(row -> {
            assertThat(row.shares()).singleElement().satisfies(share -> {
                assertThat(share.projectName()).isEqualTo("한 프로젝트");
                assertThat(share.mm()).isEqualTo(0.7);
            });
            assertThat(row.basicPct()).isEqualTo(70.0);
        });
    }

    @Test
    @DisplayName("0 M/M 배정은 원인 목록에서 빠진다 — 체크 역할은 부하가 아니다")
    void dropsZeroShares() {
        // Given: 실무자가 따로 있는 프로젝트의 PM 배정은 0이다(부록 B ③ · A6-7 기본값).
        //        시드 이현창의 롯데관광 배정이 실제로 그렇다(2026-08-24 실측)
        givenPopulation(profile(PERSON_ID, 1.0, 1.0));
        givenAssignments(
                assignment(PERSON_ID, 1L, "실무", ProjectStatus.IN_PROGRESS, 0.9),
                assignment(PERSON_ID, 2L, "PM 체크만", ProjectStatus.IN_PROGRESS, 0.0));

        // When
        List<PersonUtilization> found = calculator.calculate(CALLER_ID, query(null, null, false));

        // Then: 남기면 부하에 기여하지 않은 프로젝트를 과부하의 원인으로 내놓는다
        assertThat(found).singleElement().satisfies(row -> {
            assertThat(row.shares())
                    .extracting(PersonUtilization.ProjectShare::projectName)
                    .containsExactly("실무");
            // 분자는 그대로다 — 0을 더하지 않았을 뿐이다
            assertThat(row.assignedMm()).isEqualTo(0.9);
        });
    }

    @Test
    @DisplayName("개인 지정인데 모집단이 비면 404 — 부재와 가시성 밖을 같은 답으로 덮는다")
    void singlePersonOutsidePopulationIsNotFound() {
        when(population.resolve(eq(CALLER_ID), any())).thenReturn(List.of());

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> calculator.calculate(CALLER_ID, query(PERSON_ID, null, false)));

        // 없는 사람의 배정을 묻지 않는다 — 존재 여부가 질의 흔적으로 새지 않는다
        verify(assignmentDirectory, never()).findInMonth(any(), anyCollection());
    }

    @Test
    @DisplayName("집계에서 모집단이 비면 빈 목록이다 — 404가 아니다")
    void emptyAggregateIsEmptyList() {
        when(population.resolve(eq(CALLER_ID), any())).thenReturn(List.of());

        assertThat(calculator.calculate(CALLER_ID, query(null, null, false))).isEmpty();

        verify(assignmentDirectory, never()).findInMonth(any(), anyCollection());
    }

    @Test
    @DisplayName("배정 조회는 모집단 인원만 대상으로 한다")
    void asksAssignmentsForPopulationOnly() {
        givenPopulation(profile(PERSON_ID, 1.0, 1.0));
        givenAssignments();

        calculator.calculate(CALLER_ID, query(null, null, false));

        verify(assignmentDirectory).findInMonth(MONTH, Set.of(PERSON_ID));
    }

    // --- 픽스처 --------------------------------------------------------------

    private void givenPopulation(WorkforceProfile... profiles) {
        when(population.resolve(eq(CALLER_ID), any())).thenReturn(List.of(profiles));
    }

    private void givenAssignments(MonthlyAssignment... rows) {
        when(assignmentDirectory.findInMonth(eq(MONTH), anyCollection())).thenReturn(List.of(rows));
    }

    private static double sumOf(PersonUtilization row) {
        return row.shares().stream()
                .mapToDouble(PersonUtilization.ProjectShare::mm)
                .sum();
    }

    private static UtilizationQuery query(Long personId, Long orgUnitId, boolean overbookedOnly) {
        return new UtilizationQuery(MONTH, personId, orgUnitId, overbookedOnly);
    }

    private static WorkforceProfile profile(long personId, double capacity, double coeff) {
        return new WorkforceProfile(
                personId, "전세아", "통합검색팀", "플랫폼사업부", 8L, 2L, capacity, true, coeff);
    }

    private static MonthlyAssignment assignment(
            long personId, long projectId, String name, ProjectStatus status, double mm) {
        return new MonthlyAssignment(personId, projectId, name, status, mm);
    }

}
