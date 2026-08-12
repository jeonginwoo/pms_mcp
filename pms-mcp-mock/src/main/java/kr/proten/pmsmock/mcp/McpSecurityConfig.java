package kr.proten.pmsmock.mcp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

/**
 * /mcp 보안 체인 (구현_노트 §1-1) — 실전 계약: 승격 시 그대로 이동.
 * 검증 지점은 하나, 통과 토큰 유형은 둘(위임 JWT·PAT) — 어느 쪽이든
 * 같은 SecurityContext가 서고 이후 판정은 애플리케이션 계층 몫(원칙 3·4).
 * JwtDecoder는 주입받는다 — 목업은 HS256 대칭키(mock/), 실전은 JWKS(§1-1).
 */
@Configuration
public class McpSecurityConfig {

    @Bean
    @Order(1) // 실전에서 기존 REST 체인보다 먼저 매칭 — 구조 유지를 위해 목업에도 동일 표기
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/mcp/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * audience 검증 — 이 토큰이 "PMS용"인지 확인해 토큰 전용(오용)을 차단(§1-1).
     * 디코더 구현(HS256/JWKS)과 무관한 계약이라 여기(승격 대상)에 둔다 — 디코더가 주입받아 조합.
     */
    @Bean
    OAuth2TokenValidator<Jwt> pmsAudienceValidator() {
        return jwt -> jwt.getAudience().contains("pms")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "audience mismatch", null));
    }
}
