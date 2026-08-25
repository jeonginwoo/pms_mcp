package kr.proten.pms.notification.controller.stream;

import kr.proten.pms.common.exception.UnauthenticatedException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * {@link StreamCallerResolver} 구현 둘 — {@code pms.auth.enabled}가 하나를 고른다.
 * {@code CallerPersonIdWebConfig}와 같은 배치이고, 같은 이유로 구현을 각자
 * {@code @Component}로 두지 않고 여기서 빈으로 낸다(웹 슬라이스 테스트가 스캔하지 않는다).
 */
@Configuration
class StreamCallerConfig {

    /**
     * 인증 미사용 — 파라미터가 personId를 그대로 나른다 (기본값).
     *
     * <p>헤더를 신뢰하는 것과 <b>같은 신뢰 모델</b>이다({@code HeaderCallerIdentityResolver}):
     * 그래서 이 상태의 앱은 외부에 노출하면 안 된다는 규칙이 그대로 적용된다.
     * 부재와 비숫자를 같은 401로 수렴시키는 것도 같은 이유다 — "호출자를 못 정했다"는
     * 한 사실이 두 응답으로 새지 않게 한다.
     */
    @Bean
    @ConditionalOnProperty(name = "pms.auth.enabled", havingValue = "false", matchIfMissing = true)
    StreamCallerResolver openStreamCallerResolver() {
        return token -> {
            if (token == null || token.isBlank()) {
                throw new UnauthenticatedException("호출자 식별 정보가 없습니다");
            }

            try {
                return Long.parseLong(token.trim());
            } catch (NumberFormatException e) {
                throw new UnauthenticatedException("호출자 식별 정보가 올바르지 않습니다");
            }
        };
    }

    /**
     * 인증 사용 — 파라미터가 access 토큰이고 subject가 personId다 (PRD-pms §7).
     *
     * <p><b>보호 체인이 이 라우트를 통과시키므로 여기서 직접 검증한다</b>: 토큰이 헤더가
     * 아니라 쿼리에 있어 리소스 서버 필터가 못 읽는다. 그래서 스트림만 `permitAll`로
     * 열어 두고 검증 책임을 이 자리로 옮긴다 — 같은 {@code accessTokenDecoder} 빈을
     * 쓰므로 audience=pms·token_type=access 검사는 본 체인과 동일하다.
     *
     * <p>실패 문구에 토큰을 싣지 않는다 — 앱이 스스로 토큰을 로그에 남기지 않는 것이
     * 마스킹의 앱 쪽 몫이다(구현 노트 §6은 Nginx 로그 포맷을 요구한다).
     */
    @Bean
    @ConditionalOnProperty(name = "pms.auth.enabled", havingValue = "true")
    StreamCallerResolver tokenStreamCallerResolver(JwtDecoder accessTokenDecoder) {
        return token -> {
            if (token == null || token.isBlank()) {
                throw new UnauthenticatedException("인증이 필요합니다");
            }

            try {
                Jwt jwt = accessTokenDecoder.decode(token);

                return Long.parseLong(jwt.getSubject());
            } catch (JwtException | NumberFormatException e) {
                throw new UnauthenticatedException("토큰을 사용할 수 없습니다");
            }
        };
    }
}
