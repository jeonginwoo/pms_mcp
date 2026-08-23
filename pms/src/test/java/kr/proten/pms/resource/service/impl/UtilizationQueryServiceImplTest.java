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
import kr.proten.pms.resource.service.dto.UtilizationView;
import kr.proten.pms.resource.service.entity.Capacity;
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
 * 과부하 필터.
 *
 * <p>C1-2의 고정값(0.5+0.7 · 가용 1.0 · coeff 1.2 → 기본 120 · 보정 144)이 이 테스트의
 * 앵커다. 2026-08-10 재정의로 보정은 계수를 <b>곱한다</b> — 나누던 구 산식이면 100이
 * 나오므로 이 한 케이스가 산식 방향을 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class UtilizationQueryServiceImplTest {
    private static final YearMonth MONTH = YearMonth.of(2026, 8);
    private static final long CALLER_ID = 102L;
    private static final long PERSON_ID = 103L;

    @Mock
    private UtilizationPopulation population;
    @Mock
    private AssignmentDirectoryService assignmentDirectory;
    @Mock
    private CapacityRepository capacityRepository;
    @InjectMocks
    private UtilizationQueryServiceImpl service;

    @Test
    @DisplayName("C1-2 — A 0.5 + B 0.7, 가용 1.0, coeff 1.2 → 기본 120 · 보정 144")
    void appliesFormulaToFixedExample() {
        // Given
        givenPopulation(profile(PERSON_ID, 1.0, 1.2));
        givenAssignments(
                assignment(PERSON_ID, 1L, "A", ProjectStatus.IN_PROGRESS, 0.5),
                assignment(PERSON_ID, 2L, "B", ProjectStatus.IN_PROGRESS, 0.7));

        // When
        List<UtilizationView> found = service.find(CALLER_ID, query(null, null, false));

        // Then: 보정은 계수를 곱한다 — 나누면 100이 나온다(2026-08-10 재정의)
        assertThat(found).singleElement().satisfies(view -> {
            assertThat(view.assignedMm()).isEqualTo(1.2);
            assertThat(view.availableMm()).isEqualTo(1.0);
            assertThat(view.basic()).isEqualTo(120.0);
            assertThat(view.adjusted()).isEqualTo(144.0);
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
        List<UtilizationView> found = service.find(CALLER_ID, query(null, null, false));

        // Then: 모집단 판정은 resource의 것이다 — project는 상태라는 사실만 내준다
        assertThat(found).singleElement()
                .satisfies(view -> assertThat(view.basic()).isEqualTo(50.0));
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
        List<UtilizationView> found = service.find(CALLER_ID, query(null, null, false));

        // Then: 0.5 / 0.5 = 100 (기본값 1.0을 썼다면 50이다)
        assertThat(found).singleElement().satisfies(view -> {
            assertThat(view.availableMm()).isEqualTo(0.5);
            assertThat(view.basic()).isEqualTo(100.0);
        });
    }

    @Test
    @DisplayName("배정이 없는 사람도 0%로 목록에 남는다 — 집계는 명단이 온전해야 한다")
    void keepsUnassignedPeopleAtZero() {
        givenPopulation(profile(PERSON_ID, 1.0, 1.0));
        givenAssignments();

        assertThat(service.find(CALLER_ID, query(null, null, false)))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.assignedMm()).isZero();
                    assertThat(view.basic()).isZero();
                });
    }

    @Test
    @DisplayName("M/M 합의 부동소수점 노이즈가 응답에 새지 않는다 — 과부하 판정이 여기 걸린다")
    void stripsFloatingPointNoise() {
        // Given: 0.5 + 0.7 = 1.2 가 아니라 1.1999999999999997 이고, 계수 1.5를 곱하면
        //        보정이 179.99999999999997 이 된다(실측)
        givenPopulation(profile(PERSON_ID, 1.0, 1.5));
        givenAssignments(
                assignment(PERSON_ID, 1L, "A", ProjectStatus.IN_PROGRESS, 0.5),
                assignment(PERSON_ID, 2L, "B", ProjectStatus.IN_PROGRESS, 0.7));

        // When
        List<UtilizationView> found = service.find(CALLER_ID, query(null, null, false));

        // Then: 판정이 노이즈로 갈리면 같은 데이터가 실행마다 다른 답을 낸다
        assertThat(found).singleElement().satisfies(view -> {
            assertThat(view.basic()).isEqualTo(120.0);
            assertThat(view.adjusted()).isEqualTo(180.0);
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
        List<UtilizationView> found = service.find(CALLER_ID, query(null, null, false));

        // Then: resource는 "시스템 계정"을 알지 못하고 "분모가 없다"만 안다
        assertThat(found).extracting(UtilizationView::personId).containsExactly(200L);
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
        List<UtilizationView> found = service.find(CALLER_ID, query(null, null, true));

        // Then: 기본이 100을 넘는 200번만 남는다 (2026-08-10 — 구 "보정>100" 대체)
        assertThat(found).extracting(UtilizationView::personId).containsExactly(200L);
    }

    @Test
    @DisplayName("C1-6 — 응답에 team·division이 담긴다")
    void carriesTeamAndDivision() {
        givenPopulation(profile(PERSON_ID, 1.0, 1.0));
        givenAssignments();

        assertThat(service.find(CALLER_ID, query(null, null, false)))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.team()).isEqualTo("통합검색팀");
                    assertThat(view.division()).isEqualTo("플랫폼사업부");
                });
    }

    @Test
    @DisplayName("개인 지정인데 모집단이 비면 404 — 부재와 가시성 밖을 같은 답으로 덮는다")
    void singlePersonOutsidePopulationIsNotFound() {
        when(population.resolve(eq(CALLER_ID), any())).thenReturn(List.of());

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.find(CALLER_ID, query(PERSON_ID, null, false)));

        // 없는 사람의 배정을 묻지 않는다 — 존재 여부가 질의 흔적으로 새지 않는다
        verify(assignmentDirectory, never()).findInMonth(any(), anyCollection());
    }

    @Test
    @DisplayName("집계에서 모집단이 비면 빈 목록이다 — 404가 아니다")
    void emptyAggregateIsEmptyList() {
        when(population.resolve(eq(CALLER_ID), any())).thenReturn(List.of());

        assertThat(service.find(CALLER_ID, query(null, null, false))).isEmpty();

        verify(assignmentDirectory, never()).findInMonth(any(), anyCollection());
    }

    @Test
    @DisplayName("배정 조회는 모집단 인원만 대상으로 한다")
    void asksAssignmentsForPopulationOnly() {
        givenPopulation(profile(PERSON_ID, 1.0, 1.0));
        givenAssignments();

        service.find(CALLER_ID, query(null, null, false));

        verify(assignmentDirectory).findInMonth(MONTH, Set.of(PERSON_ID));
    }

    // --- 픽스처 --------------------------------------------------------------

    private void givenPopulation(WorkforceProfile... profiles) {
        when(population.resolve(eq(CALLER_ID), any())).thenReturn(List.of(profiles));
    }

    private void givenAssignments(MonthlyAssignment... rows) {
        when(assignmentDirectory.findInMonth(eq(MONTH), anyCollection())).thenReturn(List.of(rows));
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
