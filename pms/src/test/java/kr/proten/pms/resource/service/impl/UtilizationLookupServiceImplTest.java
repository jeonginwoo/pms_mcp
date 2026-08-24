package kr.proten.pms.resource.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.person.WorkforceDirectoryService;
import kr.proten.pms.person.WorkforceProfile;
import kr.proten.pms.resource.OverbookedBrief;
import kr.proten.pms.resource.UtilizationBrief;
import kr.proten.pms.resource.UtilizationScope;
import kr.proten.pms.resource.service.dto.UtilizationQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 루트 계약 단위 테스트 — <b>범위 해석</b>과 <b>표현 변환</b>만 본다
 * (MCP `get_utilization`·`list_overbooked`).
 *
 * <p>수치는 {@link UtilizationCalculator}가 내고 그 산식은 자기 테스트가 고정한다. 여기서
 * 계산기를 목으로 세우고 <b>어떤 조회 조건으로 불렸는지</b>를 붙잡는 이유가 그것이다:
 * 이 클래스가 틀릴 수 있는 지점은 "MY_TEAM을 부문 id로 풀었다" 같은 <b>범위 해석의 오류</b>이고,
 * 그것은 결과 숫자를 봐서는 드러나지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class UtilizationLookupServiceImplTest {
    private static final YearMonth MONTH = YearMonth.of(2026, 8);
    private static final long CALLER_ID = 102L;
    private static final long OTHER_ID = 103L;
    private static final long TEAM_ORG_ID = 8L;
    private static final long DIVISION_ORG_ID = 2L;

    @Mock
    private UtilizationCalculator calculator;
    @Mock
    private WorkforceDirectoryService workforceDirectory;
    @InjectMocks
    private UtilizationLookupServiceImpl service;

    @Test
    @DisplayName("scope=ME — 화자 자신의 개인 지정 조회다 (집계가 아니라 billable과 무관)")
    void meResolvesToTheCallerAsSinglePerson() {
        givenRows();

        service.find(CALLER_ID, MONTH, UtilizationScope.ME, null);

        UtilizationQuery query = capturedQuery();
        assertThat(query.personId()).isEqualTo(CALLER_ID);
        assertThat(query.isSinglePerson()).isTrue();
        assertThat(query.orgUnitId()).isNull();
        // 조직을 물을 일이 없다 — 화자 프로필을 읽는 것은 MY_TEAM·DIVISION뿐이다
        verify(workforceDirectory, never()).findProfiles(any());
    }

    @Test
    @DisplayName("scope=PERSON — 지정한 개인이 대상이다 (화자가 아니다)")
    void personResolvesToTheGivenTarget() {
        givenRows();

        service.find(CALLER_ID, MONTH, UtilizationScope.PERSON, OTHER_ID);

        assertThat(capturedQuery().personId()).isEqualTo(OTHER_ID);
    }

    @Test
    @DisplayName("scope=PERSON인데 personId가 없으면 400 — 조용히 본인으로 바꾸지 않는다")
    void personWithoutTargetIsRejected() {
        // 본인으로 떨어뜨리면 "그 사람 가동률"을 물은 사용자가 자기 값을 맞는 답으로 받는다
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.find(CALLER_ID, MONTH, UtilizationScope.PERSON, null));

        verify(calculator, never()).calculate(anyLong(), any());
    }

    @Test
    @DisplayName("scope=MY_TEAM — 화자의 팀 조직 id로 집계한다")
    void myTeamResolvesToTheCallerTeamOrgUnit() {
        givenCallerProfile();
        givenRows();

        service.find(CALLER_ID, MONTH, UtilizationScope.MY_TEAM, null);

        UtilizationQuery query = capturedQuery();
        assertThat(query.orgUnitId()).isEqualTo(TEAM_ORG_ID);
        assertThat(query.personId()).isNull();
    }

    @Test
    @DisplayName("scope=DIVISION — 화자의 부문 조직 id로 집계한다 (팀 id가 아니다)")
    void divisionResolvesToTheCallerDivisionOrgUnit() {
        givenCallerProfile();
        givenRows();

        service.find(CALLER_ID, MONTH, UtilizationScope.DIVISION, null);

        assertThat(capturedQuery().orgUnitId()).isEqualTo(DIVISION_ORG_ID);
    }

    @Test
    @DisplayName("scope=COMPANY — 조직을 지정하지 않는다. 상한은 화자의 가시성이다")
    void companyLeavesTheOrgUnitOpen() {
        givenRows();

        service.find(CALLER_ID, MONTH, UtilizationScope.COMPANY, null);

        // 전사 전용 플래그를 두면 관리자가 아닌 화자에게 "전사라고 물었는데 일부만 왔다"를
        // 설명할 방법이 없어진다 — 비워 두면 모집단 판정이 그대로 가시성을 상한으로 쓴다
        UtilizationQuery query = capturedQuery();
        assertThat(query.personId()).isNull();
        assertThat(query.orgUnitId()).isNull();
        assertThat(query.overbookedOnly()).isFalse();
    }

    @Test
    @DisplayName("scope이 없으면 400 — 임의로 넓은 범위로 해석하지 않는다")
    void nullScopeIsRejected() {
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.find(CALLER_ID, MONTH, null, null));

        verify(calculator, never()).calculate(anyLong(), any());
    }

    @Test
    @DisplayName("화자의 소속을 찾을 수 없으면 404 — 범위 해석의 실패가 아니라 부재다")
    void unknownCallerIsNotFound() {
        when(workforceDirectory.findProfiles(Set.of(CALLER_ID))).thenReturn(List.of());

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.find(CALLER_ID, MONTH, UtilizationScope.MY_TEAM, null));
    }

    @Test
    @DisplayName("C1-6 — 응답에 팀·부문이 함께 담긴다 (소속별로 묶을 수 있게)")
    void briefCarriesTeamAndDivision() {
        givenRows(row(CALLER_ID, 1.2, 1.0, 120.0, 144.0));

        List<UtilizationBrief> found = service.find(CALLER_ID, MONTH, UtilizationScope.ME, null);

        assertThat(found).singleElement().satisfies(brief -> {
            assertThat(brief.personId()).isEqualTo(CALLER_ID);
            assertThat(brief.name()).isEqualTo("전세아");
            assertThat(brief.team()).isEqualTo("통합검색팀");
            assertThat(brief.division()).isEqualTo("플랫폼사업부");
            assertThat(brief.month()).isEqualTo(MONTH);
            assertThat(brief.assignedMm()).isEqualTo(1.2);
            assertThat(brief.availableMm()).isEqualTo(1.0);
            assertThat(brief.basicPct()).isEqualTo(120.0);
            assertThat(brief.adjustedPct()).isEqualTo(144.0);
        });
    }

    @Test
    @DisplayName("list_overbooked — 과부하만 달라는 조회다 (범위는 가시성 상한)")
    void overbookedAsksForOverbookedOnlyWithinVisibility() {
        givenRows();

        service.findOverbooked(CALLER_ID, MONTH);

        UtilizationQuery query = capturedQuery();
        assertThat(query.overbookedOnly()).isTrue();
        assertThat(query.personId()).isNull();
        assertThat(query.orgUnitId()).isNull();
    }

    @Test
    @DisplayName("list_overbooked — 원인이 프로젝트별로 실린다 (부문은 싣지 않는다)")
    void overbookedCarriesCauses() {
        givenRows(row(CALLER_ID, 1.4, 1.0, 140.0, 168.0,
                new PersonUtilization.ProjectShare("큰 것", 0.9),
                new PersonUtilization.ProjectShare("작은 것", 0.5)));

        List<OverbookedBrief> found = service.findOverbooked(CALLER_ID, MONTH);

        assertThat(found).singleElement().satisfies(brief -> {
            assertThat(brief.personId()).isEqualTo(CALLER_ID);
            assertThat(brief.team()).isEqualTo("통합검색팀");
            assertThat(brief.basicPct()).isEqualTo(140.0);
            assertThat(brief.causes())
                    .extracting(OverbookedBrief.Cause::projectName, OverbookedBrief.Cause::mm)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("큰 것", 0.9),
                            org.assertj.core.groups.Tuple.tuple("작은 것", 0.5));
        });
    }

    // --- 픽스처 --------------------------------------------------------------

    private void givenCallerProfile() {
        when(workforceDirectory.findProfiles(Set.of(CALLER_ID)))
                .thenReturn(List.of(profile(CALLER_ID)));
    }

    private void givenRows(PersonUtilization... rows) {
        when(calculator.calculate(eq(CALLER_ID), any())).thenReturn(List.of(rows));
    }

    private UtilizationQuery capturedQuery() {
        ArgumentCaptor<UtilizationQuery> captor = ArgumentCaptor.forClass(UtilizationQuery.class);
        verify(calculator).calculate(eq(CALLER_ID), captor.capture());

        assertThat(captor.getValue().month()).isEqualTo(MONTH);

        return captor.getValue();
    }

    private static PersonUtilization row(
            long personId,
            double assignedMm,
            double availableMm,
            double basicPct,
            double adjustedPct,
            PersonUtilization.ProjectShare... shares) {
        return new PersonUtilization(
                profile(personId), MONTH, assignedMm, availableMm, basicPct, adjustedPct,
                List.of(shares));
    }

    private static WorkforceProfile profile(long personId) {
        return new WorkforceProfile(
                personId,
                "전세아",
                "통합검색팀",
                "플랫폼사업부",
                TEAM_ORG_ID,
                DIVISION_ORG_ID,
                1.0,
                true,
                1.2);
    }

}
