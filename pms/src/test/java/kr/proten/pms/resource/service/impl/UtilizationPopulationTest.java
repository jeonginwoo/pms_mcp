package kr.proten.pms.resource.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import kr.proten.pms.person.OrgVisibility;
import kr.proten.pms.person.OrgVisibilityService;
import kr.proten.pms.person.WorkforceDirectoryService;
import kr.proten.pms.person.WorkforceProfile;
import kr.proten.pms.resource.service.dto.UtilizationQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 가동률 모집단 판정 단위 테스트 (AC C1-1 범위 · C1-5 billable).
 *
 * <p>이 클래스가 답하는 질문은 하나다 — <b>누구를 셀 것인가</b>. 산식과 과부하 판정은
 * {@code UtilizationQueryServiceImpl}의 몫이라 여기서 보지 않는다.
 *
 * <p>규칙이 갈리는 지점이 둘이라 케이스가 그 축을 따라간다: <b>개인 지정 vs 집계</b>
 * (C1-5는 집계에만 적용된다)와 <b>전사 scope vs 제한 scope</b>(전사는 가시성이
 * 인원 집합을 비워 두므로 명단을 따로 얻어야 한다).
 */
@ExtendWith(MockitoExtension.class)
class UtilizationPopulationTest {
    private static final YearMonth MONTH = YearMonth.of(2026, 8);
    private static final long CALLER_ID = 102L;
    private static final long TEAMMATE_ID = 103L;
    private static final long OUTSIDER_ID = 900L;
    private static final long TEAM_ORG_UNIT_ID = 8L;

    @Mock
    private OrgVisibilityService orgVisibilityService;
    @Mock
    private WorkforceDirectoryService workforceDirectory;
    @InjectMocks
    private UtilizationPopulation population;

    // --- 개인 지정 — C1-5가 적용되지 않는다 -----------------------------------

    @Test
    @DisplayName("개인 지정 — billable=false 인 사람도 자기 가동률은 나온다 (C1-5)")
    void singlePerson_ignoresBillable() {
        // Given: 집계라면 모집단에서 빠질 사람
        given(OrgVisibility.of(CALLER_ID, Set.of(TEAMMATE_ID)));
        when(workforceDirectory.findProfiles(Set.of(TEAMMATE_ID)))
                .thenReturn(List.of(profile(TEAMMATE_ID, 1.0, false)));

        // When: 개인 지정으로 물었다
        List<WorkforceProfile> found =
                population.resolve(CALLER_ID, new UtilizationQuery(MONTH, TEAMMATE_ID, null, false));

        // Then: 개인 지정은 billable과 무관하다 (상위 PRD §3 · 2026-08-06)
        assertThat(found).extracting(WorkforceProfile::personId).containsExactly(TEAMMATE_ID);
    }

    @Test
    @DisplayName("개인 지정 — 가시성 밖 인원은 빈 결과다 (404 문구는 유스케이스가 정한다)")
    void singlePerson_outsideVisibility_isEmpty() {
        given(OrgVisibility.of(CALLER_ID, Set.of(TEAMMATE_ID)));

        List<WorkforceProfile> found =
                population.resolve(CALLER_ID, new UtilizationQuery(MONTH, OUTSIDER_ID, null, false));

        // 가시성 판정에서 걸렸으면 인원 조회까지 가지 않는다 — 존재 여부가 새지 않는다
        assertThat(found).isEmpty();
        verify(workforceDirectory, never()).findProfiles(anyCollection());
    }

    // --- 집계 — 범위 결정 ------------------------------------------------------

    @Test
    @DisplayName("집계 + orgUnitId — subtree와 가시성의 교집합이다")
    void aggregate_withOrgUnit_intersectsVisibility() {
        given(OrgVisibility.of(CALLER_ID, Set.of(TEAMMATE_ID)));
        // subtree에는 가시성 밖 인원도 들어 있다 — 교집합으로 걸러져야 한다
        when(workforceDirectory.findPersonIdsInSubtree(TEAM_ORG_UNIT_ID))
                .thenReturn(Set.of(TEAMMATE_ID, OUTSIDER_ID));
        when(workforceDirectory.findProfiles(Set.of(TEAMMATE_ID)))
                .thenReturn(List.of(profile(TEAMMATE_ID, 1.0, true)));

        List<WorkforceProfile> found = population.resolve(
                CALLER_ID, new UtilizationQuery(MONTH, null, TEAM_ORG_UNIT_ID, false));

        assertThat(found).extracting(WorkforceProfile::personId).containsExactly(TEAMMATE_ID);
    }

    @Test
    @DisplayName("집계 + 전사 scope — 가시성이 인원 집합을 비워 두므로 전체 명단을 얻는다")
    void aggregate_unrestricted_usesFullRoster() {
        // Given: unrestricted는 "제약 없음"이라 visiblePersonIds가 비어 있다 —
        //        그것을 그대로 쓰면 전사 관리자에게 아무도 보이지 않는다
        given(OrgVisibility.unrestricted(CALLER_ID));
        when(workforceDirectory.findAllAggregatablePersonIds())
                .thenReturn(Set.of(CALLER_ID, TEAMMATE_ID));
        when(workforceDirectory.findProfiles(Set.of(CALLER_ID, TEAMMATE_ID)))
                .thenReturn(List.of(profile(CALLER_ID, 1.0, true), profile(TEAMMATE_ID, 1.0, true)));

        List<WorkforceProfile> found =
                population.resolve(CALLER_ID, new UtilizationQuery(MONTH, null, null, false));

        assertThat(found).extracting(WorkforceProfile::personId)
                .containsExactlyInAnyOrder(CALLER_ID, TEAMMATE_ID);
    }

    @Test
    @DisplayName("집계 + 제한 scope — 명단을 따로 묻지 않고 가시 인원을 쓴다")
    void aggregate_restricted_usesVisiblePeople() {
        given(OrgVisibility.of(CALLER_ID, Set.of(TEAMMATE_ID)));
        when(workforceDirectory.findProfiles(Set.of(CALLER_ID, TEAMMATE_ID)))
                .thenReturn(List.of(profile(CALLER_ID, 1.0, true), profile(TEAMMATE_ID, 1.0, true)));

        population.resolve(CALLER_ID, new UtilizationQuery(MONTH, null, null, false));

        // 제한 scope에서 전체 명단을 부르면 조직 밖 인원이 집계에 섞인다
        verify(workforceDirectory, never()).findAllAggregatablePersonIds();
    }

    // --- 집계 — 모집단 규칙 ----------------------------------------------------

    @Test
    @DisplayName("C1-5 — 집계에서는 billable=false 인원이 모집단에서 빠진다")
    void aggregate_excludesNonBillable() {
        given(OrgVisibility.of(CALLER_ID, Set.of(TEAMMATE_ID)));
        when(workforceDirectory.findProfiles(Set.of(CALLER_ID, TEAMMATE_ID)))
                .thenReturn(List.of(
                        profile(CALLER_ID, 1.0, true), profile(TEAMMATE_ID, 1.0, false)));

        List<WorkforceProfile> found =
                population.resolve(CALLER_ID, new UtilizationQuery(MONTH, null, null, false));

        assertThat(found).extracting(WorkforceProfile::personId).containsExactly(CALLER_ID);
    }

    @Test
    @DisplayName("가시 인원이 없으면 인원 조회를 하지 않는다")
    void aggregate_emptyScope_skipsQuery() {
        given(OrgVisibility.of(CALLER_ID, Set.of()));
        when(workforceDirectory.findPersonIdsInSubtree(TEAM_ORG_UNIT_ID)).thenReturn(Set.of());

        assertThat(population.resolve(
                CALLER_ID, new UtilizationQuery(MONTH, null, TEAM_ORG_UNIT_ID, false))).isEmpty();

        verify(workforceDirectory, never()).findProfiles(anyCollection());
    }

    @Test
    @DisplayName("가시성 조회는 화자당 한 번이다 — 판정이 두 번 갈리지 않는다")
    void resolvesVisibilityOnce() {
        given(OrgVisibility.of(CALLER_ID, Set.of(TEAMMATE_ID)));
        when(workforceDirectory.findProfiles(anyCollection()))
                .thenReturn(List.of(profile(CALLER_ID, 1.0, true)));

        population.resolve(CALLER_ID, new UtilizationQuery(MONTH, null, null, false));

        verify(orgVisibilityService).visibilityOf(anyLong());
    }

    private void given(OrgVisibility visibility) {
        when(orgVisibilityService.visibilityOf(CALLER_ID)).thenReturn(visibility);
    }

    private static WorkforceProfile profile(long personId, double capacity, boolean billable) {
        return new WorkforceProfile(
                personId, "전세아", "통합검색팀", "플랫폼사업부",
                TEAM_ORG_UNIT_ID, 2L, capacity, billable, 1.2);
    }
}
