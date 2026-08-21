package kr.proten.pms.person.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 조직 가시성 판정 결과 VO 단위 테스트 (상위 PRD §4-4).
 * 모듈 경계를 넘는 값이므로 대상 인원의 조직을 몰라도 판정이 끝나 있어야 한다 —
 * project 모듈은 이 VO만 보고 프로젝트 가시성을 계산한다.
 */
class OrgVisibilityTest {
    @Test
    @DisplayName("전사 scope — 어떤 인원도 보인다")
    void canView_unrestricted_seesAnyone() {
        OrgVisibility visibility = OrgVisibility.unrestricted(1L);

        assertThat(visibility.canView(999L)).isTrue();
        assertThat(visibility.unrestricted()).isTrue();
    }

    @Test
    @DisplayName("제한 scope — 집합 안의 인원만 보인다")
    void canView_restricted_seesOnlyListed() {
        OrgVisibility visibility = OrgVisibility.of(10L, Set.of(11L, 12L));

        assertThat(visibility.canView(11L)).isTrue();
        assertThat(visibility.canView(13L)).isFalse();
    }

    @Test
    @DisplayName("본인은 scope와 무관하게 항상 보인다 — 집합에 없어도")
    void canView_self_isAlwaysVisible() {
        OrgVisibility visibility = OrgVisibility.of(10L, Set.of());

        assertThat(visibility.canView(10L)).isTrue();
        assertThat(visibility.visiblePersonIds()).contains(10L);
    }
}
