package kr.proten.pmsmock.mock;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.proten.pmsmock.MockData;
import kr.proten.pmsmock.port.ToolError;
import kr.proten.pmsmock.port.dto.OverbookedEntry;
import kr.proten.pmsmock.port.dto.UtilizationEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UtilizationServiceTest {

    private static final int 신현랑_관리자 = 1;
    private static final int 정태휘_부문장 = 13;
    private static final int 남도린_팀장 = 16;
    private static final int 남민준 = 17;
    private static final int 전세아 = 18;
    private static final int 조예아 = 19;

    private InMemoryUtilizationQueryService service;

    @BeforeEach
    void setUp() {
        MockData data = new MockData();
        service = new InMemoryUtilizationQueryService(data, new VisibilityPolicy(data));
    }

    @Test
    @DisplayName("심은 오버부킹: 2026-08 전세아 1.3MM — 기본 130% / 보정 162.5% (coeff 0.8)")
    void plantedOverbooking() {
        UtilizationEntry e = service.getUtilization(전세아, "2026-08", "ME", null).getFirst();
        assertThat(e.assignedMm()).isEqualTo(1.3);
        assertThat(e.basicPct()).isEqualTo(130.0);
        assertThat(e.adjustedPct()).isEqualTo(162.5);
    }

    @Test
    @DisplayName("경계값: 2026-09 전세아 0.8MM — 보정 정확히 100%는 오버부킹 아님")
    void boundaryNotOverbooked() {
        UtilizationEntry e = service.getUtilization(전세아, "2026-09", "ME", null).getFirst();
        assertThat(e.adjustedPct()).isEqualTo(100.0);
        assertThat(service.listOverbooked(정태휘_부문장, "2026-09"))
                .extracting(OverbookedEntry::personId)
                .doesNotContain(전세아);
    }

    @Test
    @DisplayName("list_overbooked 2026-08 (부문장): 전세아·남민준 — 보정 내림차순 + 원인 배정 동봉")
    void overbookedAugust() {
        List<OverbookedEntry> result = service.listOverbooked(정태휘_부문장, "2026-08");
        assertThat(result).extracting(OverbookedEntry::personId).containsExactly(전세아, 남민준);
        assertThat(result.getFirst().causes()).hasSize(3); // SK온 EUE·우리은행·치과재료
    }

    @Test
    @DisplayName("원인 배정도 프로젝트 가시성 필터 — 팀장에게 팀 밖 프로젝트(우리은행)는 원인에서 제외")
    void causesFilteredByProjectVisibility() {
        List<OverbookedEntry> result = service.listOverbooked(남도린_팀장, "2026-08");
        OverbookedEntry 전세아_entry = result.getFirst();
        assertThat(전세아_entry.personId()).isEqualTo(전세아);
        assertThat(전세아_entry.causes())
                .extracting(OverbookedEntry.Cause::projectName)
                .containsExactlyInAnyOrder("SK온 EUE공장 문서검색엔진 구축", "치과재료 쇼핑몰 내 검색엔진 구축")
                .doesNotContain("우리은행 문서중앙화 구축"); // 남도린 가시성(팀) 밖 — 단건 조회면 404 은닉인 프로젝트
        // 부문장에게는 셋 다 보인다 (전부 자기 부문)
        assertThat(service.listOverbooked(정태휘_부문장, "2026-08").getFirst().causes()).hasSize(3);
    }

    @Test
    @DisplayName("집계 모집단 = billable=true — 관리자 전사 조회에도 신현랑(billable=false) 미포함")
    void billableExcludedFromAggregates() {
        assertThat(service.listOverbooked(신현랑_관리자, "2026-08"))
                .extracting(OverbookedEntry::personId)
                .doesNotContain(신현랑_관리자);
        // MY_TEAM 집계에서도 마찬가지 — 신현랑 자신의 팀 집계는 빈 목록(팀 유일 인원이 billable=false)
        assertThat(service.getUtilization(신현랑_관리자, "2026-08", "MY_TEAM", null)).isEmpty();
    }

    @Test
    @DisplayName("ME는 billable 무관 — 개인 지정 조회도 무관 (상위 PRD §3)")
    void meIgnoresBillable() {
        assertThat(service.getUtilization(신현랑_관리자, "2026-08", "ME", null)).hasSize(1);
    }

    @Test
    @DisplayName("팀원의 MY_TEAM/DIVISION 집계는 은닉 — 팀장의 MY_TEAM은 팀 5명(billable) 반환")
    void aggregateScopeByGroup() {
        assertThatThrownBy(() -> service.getUtilization(조예아, "2026-08", "MY_TEAM", null))
                .isInstanceOf(ToolError.class)
                .hasMessageContaining("조회 가능한 범위");
        assertThat(service.getUtilization(남도린_팀장, "2026-08", "MY_TEAM", null)).hasSize(5);
    }

    @Test
    @DisplayName("PERSON 지정: 부문장은 부문 내 개인 조회 가능, 팀원은 타인 은닉")
    void personScopeVisibility() {
        assertThat(service.getUtilization(정태휘_부문장, "2026-09", "PERSON", 전세아)).hasSize(1);
        assertThatThrownBy(() -> service.getUtilization(조예아, "2026-08", "PERSON", 남민준))
                .isInstanceOf(ToolError.class)
                .hasMessageContaining("조회 가능한 범위");
    }

    @Test
    @DisplayName("month 형식 오류는 422")
    void invalidMonth() {
        assertThatThrownBy(() -> service.getUtilization(전세아, "8월", "ME", null))
                .isInstanceOf(ToolError.class)
                .hasMessageContaining("[422");
    }
}
