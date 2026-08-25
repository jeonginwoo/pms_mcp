package kr.proten.pms.notification.controller.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import kr.proten.pms.common.exception.UnauthenticatedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * 스트림 전용 호출자 식별 (PRD-pms §7 — `?access_token=`).
 *
 * <p>두 모드가 <b>같은 401로 수렴</b>하는 것이 여기서 잠그는 규칙이다: 부재·형식 오류·
 * 검증 실패가 서로 다른 응답으로 갈리면 "호출자를 못 정했다"는 한 사실이 여러 답으로
 * 샌다({@code HeaderCallerIdentityResolver}가 같은 근거로 그렇게 돼 있다).
 */
class StreamCallerResolverTest {
    private final StreamCallerConfig config = new StreamCallerConfig();

    @Test
    @DisplayName("인증 OFF — 파라미터가 personId를 그대로 나른다 (헤더와 같은 신뢰 모델)")
    void openModeTrustsTheParameter() {
        assertThat(config.openStreamCallerResolver().resolve("13")).isEqualTo(13L);
        // 공백은 다듬는다 — 헤더 리졸버와 같은 관용이다
        assertThat(config.openStreamCallerResolver().resolve(" 13 ")).isEqualTo(13L);
    }

    @Test
    @DisplayName("인증 OFF — 부재와 비숫자가 같은 401이다")
    void openModeConvergesOnUnauthenticated() {
        StreamCallerResolver resolver = config.openStreamCallerResolver();

        assertThatExceptionOfType(UnauthenticatedException.class)
                .isThrownBy(() -> resolver.resolve(null));
        assertThatExceptionOfType(UnauthenticatedException.class)
                .isThrownBy(() -> resolver.resolve("  "));
        assertThatExceptionOfType(UnauthenticatedException.class)
                .isThrownBy(() -> resolver.resolve("나"));
    }

    @Test
    @DisplayName("인증 ON — 토큰 subject가 personId다 (본 체인과 같은 디코더)")
    void tokenModeUsesTheSubject() {
        // Given
        JwtDecoder decoder = mock(JwtDecoder.class);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("13").build();
        when(decoder.decode("t")).thenReturn(jwt);

        // When · Then
        assertThat(config.tokenStreamCallerResolver(decoder).resolve("t")).isEqualTo(13L);
    }

    @Test
    @DisplayName("인증 ON — 검증 실패·부재가 같은 401이고 문구에 토큰이 없다")
    void tokenModeRejectsWithoutLeakingTheToken() {
        // Given
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("bad")).thenThrow(new BadJwtException("만료"));
        StreamCallerResolver resolver = config.tokenStreamCallerResolver(decoder);

        // When · Then
        assertThatExceptionOfType(UnauthenticatedException.class)
                .isThrownBy(() -> resolver.resolve(null));
        assertThatExceptionOfType(UnauthenticatedException.class)
                .isThrownBy(() -> resolver.resolve("bad"))
                // 앱이 스스로 토큰을 남기지 않는 것이 마스킹의 앱 쪽 몫이다(구현 노트 §6)
                .satisfies(thrown -> assertThat(thrown.getMessage()).doesNotContain("bad"));
    }

    @Test
    @DisplayName("인증 ON — subject가 숫자가 아니면 401이다 (구성 결함도 열어 두지 않는다)")
    void tokenModeRejectsNonNumericSubject() {
        // Given
        JwtDecoder decoder = mock(JwtDecoder.class);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("사람").build();
        when(decoder.decode("t")).thenReturn(jwt);

        // When · Then
        assertThatExceptionOfType(UnauthenticatedException.class)
                .isThrownBy(() -> config.tokenStreamCallerResolver(decoder).resolve("t"));
    }
}
