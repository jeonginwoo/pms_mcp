package kr.proten.pms.project.service.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import kr.proten.pms.common.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 프로젝트 중복 판정 키 단위 테스트 — 정규화 규칙(AC A1-2)의 유일 지점.
 * 정규화 = trim · 연속 공백 1개로 축약 · 영문 대소문자 무시.
 */
class ProjectKeyTest {
    @Test
    @DisplayName("앞뒤 공백과 연속 공백은 같은 이름으로 접힌다")
    void normalized_collapsesWhitespace() {
        ProjectKey spaced = new ProjectKey("  (주)가온아이 ", "그룹웨어   재구축");
        ProjectKey plain = new ProjectKey("(주)가온아이", "그룹웨어 재구축");

        assertThat(spaced.normalizedClient()).isEqualTo(plain.normalizedClient());
        assertThat(spaced.normalizedName()).isEqualTo(plain.normalizedName());
    }

    @Test
    @DisplayName("영문 대소문자는 무시한다")
    void normalized_ignoresLetterCase() {
        ProjectKey upper = new ProjectKey("GAONI", "Portal ReBuild");
        ProjectKey lower = new ProjectKey("gaoni", "portal rebuild");

        assertThat(upper.normalizedClient()).isEqualTo(lower.normalizedClient());
        assertThat(upper.normalizedName()).isEqualTo(lower.normalizedName());
    }

    @Test
    @DisplayName("원본 표기는 그대로 보존한다 — 정규화는 판정용")
    void rawValues_arePreserved() {
        ProjectKey key = new ProjectKey(" (주)가온아이 ", "Portal  ReBuild");

        assertThat(key.client()).isEqualTo(" (주)가온아이 ");
        assertThat(key.name()).isEqualTo("Portal  ReBuild");
    }

    @Test
    @DisplayName("고객사·이름 누락은 형식 오류(400)")
    void blankValues_rejected() {
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> new ProjectKey(" ", "이름"));
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> new ProjectKey("고객사", null));
    }
}
