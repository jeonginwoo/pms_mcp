package kr.proten.pms.mcp.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * /mcp 보안 체인 (구현_노트 §1-1 — 2026-08-18 M0 승격분의 재승격).
 *
 * `@Order(1)`로 자기 경로만 먼저 잡는다 — 그 밖의 요청은 이 체인을 타지 않고
 * common의 전면 허용 체인(인증 off) 또는 auth의 보호 체인(인증 on)이 처리한다.
 * 그래서 **`pms.auth.enabled`가 꺼져 있어도 `/mcp`는 토큰을 요구한다**: 웹은 헤더로
 * 호출자를 받는 개발 편의 상태여도 MCP 경로에 그 편의를 열면 원칙 4(사용자 토큰
 * 패스스루)가 무의미해진다.
 *
 * 디코더를 새로 만들지 않고 auth가 내는 `accessTokenDecoder`를 그대로 받는 이유가
 * 두 개다. ①검증 정책이 같다 — audience=pms(원칙 4)와 token_type=access가 이미
 * 그 빈에 붙어 있고(AuthKeyConfig), refresh 토큰의 `/mcp` 오용은 그 검증자가 막는다.
 * ②`JwtDecoder` 빈을 하나 더 만들면 타입 주입 지점이 모호해져 **MCP를 추가한 쪽이
 * 웹 인증을 깨뜨린다.** 정책이 갈라지는 토큰 유형(PAT — 구현_노트 §1-3)이 실제로
 * 생기면 그때 auth가 변형 디코더를 함께 내는 것이 맞는 방향이다.
 */
@Configuration
class McpSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain mcpSecurityFilterChain(
            HttpSecurity http, JwtDecoder accessTokenDecoder) throws Exception {
        http.securityMatcher("/mcp/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(accessTokenDecoder)));

        return http.build();
    }
}
