package kr.proten.pms.identity.internal.web;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.proten.pms.common.ErrorResponse;
import kr.proten.pms.identity.internal.infra.security.ApiTokenVerification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * REST 보안 체인 — JWT 리소스 서버 (§7: Bearer 필수, 없으면 401 봉투).
 * /mcp 체인은 MCP 담당이 @Order(1)·securityMatcher("/mcp/**")로 별도 구성
 * (구현_노트 §1-1) — 이 체인은 그 뒤 순번으로 나머지 전부를 담당한다.
 * 디코더는 명시 지정(ApiTokenVerification) — 타입 조회로 /mcp의 JwtDecoder 빈을
 * 집어가지 않도록.
 */
@Configuration
@EnableWebSecurity
class ApiSecurityConfig {
    // 401 봉투-로그 상관 기록용 (conventions §4 "traceId must trace" — 토큰 원문 로그 금지)
    private static final Logger log = LoggerFactory.getLogger(ApiSecurityConfig.class);

    @Bean
    @Order(10)
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            ApiTokenVerification apiTokenVerification) throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, exception) ->
                writeUnauthenticated(request, response, objectMapper);
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/jwks")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.decoder(apiTokenVerification.decoder()))
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint));

        return http.build();
    }

    /** 401을 §7 에러 봉투로 내려준다 — 기본 응답(본문 없는 WWW-Authenticate)을 대체. */
    private void writeUnauthenticated(
            HttpServletRequest request, HttpServletResponse response, ObjectMapper objectMapper)
            throws java.io.IOException {
        ErrorResponse envelope = ErrorResponse.of("UNAUTHENTICATED", "인증이 필요합니다", null);
        log.warn("에러 봉투 401 UNAUTHENTICATED uri={} traceId={}",
                request.getRequestURI(), envelope.error().traceId());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), envelope);
    }
}
