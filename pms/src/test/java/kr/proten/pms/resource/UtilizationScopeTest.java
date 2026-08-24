package kr.proten.pms.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * `scope` 낱말 해석 (MCP `get_utilization` 파라미터).
 *
 * <p>이 테스트가 지키는 것은 하나다: <b>모르는 낱말이 조용히 통과하지 않는다</b>.
 * B2-1 자연어 검증에서 모델이 카탈로그에 없는 `scope=COMPANY`를 지어낸 적이 있고
 * (그 실측이 도구를 8종으로 넓힌 근거였다), 임의로 넓은 범위로 떨어뜨리면 사용자는
 * 틀린 범위의 답을 맞는 답으로 받는다 — `ProjectLookupService`의 상태 라벨과 같은 규칙이다.
 */
class UtilizationScopeTest {

    @ParameterizedTest
    @ValueSource(strings = {"ME", "MY_TEAM", "DIVISION", "COMPANY", "PERSON"})
    @DisplayName("카탈로그의 5종을 해석한다")
    void resolvesCatalogValues(String raw) {
        assertThat(UtilizationScope.from(raw)).isEqualTo(UtilizationScope.valueOf(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"me", "My_Team", " DIVISION ", "company"})
    @DisplayName("대소문자·공백은 흡수한다 — 모델의 표기 흔들림은 오류가 아니다")
    void absorbsCaseAndWhitespace(String raw) {
        assertThat(UtilizationScope.from(raw)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPANY_WIDE", "TEAM", "전사", "ALL"})
    @DisplayName("카탈로그에 없는 낱말은 400 — 넓은 범위로 떨어뜨리지 않는다")
    void rejectsInventedValues(String raw) {
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> UtilizationScope.from(raw))
                .satisfies(thrown -> {
                    assertThat(thrown.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(thrown.field()).isEqualTo("scope");
                });
    }

    @Test
    @DisplayName("빈 값·null도 400 — 필수 파라미터다")
    void rejectsMissingValue() {
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> UtilizationScope.from(null));
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> UtilizationScope.from("  "));
    }

}
