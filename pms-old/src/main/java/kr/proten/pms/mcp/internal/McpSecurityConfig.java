package kr.proten.pms.mcp.internal;

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
 * /mcp 보안 체인 (구현_노트 §1-1 — pms-mcp-mock B2-2 검증분 승격).
 * 검증 지점은 하나, 통과 토큰 유형은 둘(위임 JWT·PAT) — 어느 쪽이든
 * 같은 SecurityContext가 서고 이후 판정은 애플리케이션 계층 몫(원칙 3·4).
 * JwtDecoder는 McpJwtDecoderConfig가 공급(JWKS 우선, 없으면 로컬 HS256).
 * /mcp 밖 요청은 이 체인을 타지 않는다 — 웹 REST 체인은 PMS-M1(pms 트랙) 몫.
 */
@Configuration
public class McpSecurityConfig {

    @Bean
    @Order(1) // 기존 REST 체인(PMS-M1)보다 먼저 매칭
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

    /**
     * 토큰 유형 검증 — 장수명 refresh 토큰(PMS-M1a 로그인 발급, aud=pms·서명 정상)의
     * /mcp 오용을 차단한다. 허용 = 무클레임(위임 JWT §1-2·HS256 테스트 토큰) ·
     * access(로그인) · pat(§1-3) — 그 외 유형은 기본 거절(fail-closed).
     * audience와 같은 이유로 디코더 구현(HS256/JWKS)과 무관한 계약이라 여기에 둔다.
     */
    @Bean
    OAuth2TokenValidator<Jwt> pmsTokenTypeValidator() {
        return jwt -> {
            String type = jwt.getClaimAsString("token_type");
            return type == null || "access".equals(type) || "pat".equals(type)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                            new OAuth2Error("invalid_token", "token_type not allowed for /mcp", null));
        };
    }
}
