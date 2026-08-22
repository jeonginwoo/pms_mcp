package kr.proten.pms.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.web.ApiError;
import kr.proten.pms.common.web.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

/**
 * 보호 체인 — JWT 리소스 서버 (PRD-pms §7: Bearer 필수, 없으면 401 봉투).
 *
 * `pms.auth.enabled=true`일 때만 구성된다. 꺼진 동안은 common의 전면 허용 체인이
 * 대신 걸리고 호출자는 헤더로 들어온다(2026-08-21 결정 — 만들어 두고 나중에 쓴다).
 * 디코더는 `JwtDecoder` 빈을 타입으로 주입받는다 — audience=pms·token_type=access
 * 검증이 그 빈에 부착돼 있다(AuthKeyConfig).
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "pms.auth.enabled", havingValue = "true")
class ApiSecurityConfig {
    // 401 봉투-로그 상관 기록용 (conventions §4 — 토큰 원문은 로그에 남기지 않는다)
    private static final Logger log = LoggerFactory.getLogger(ApiSecurityConfig.class);

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            JwtDecoder accessTokenDecoder) throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, exception) ->
                writeUnauthenticated(request, response, objectMapper);
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/jwks")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.decoder(accessTokenDecoder))
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint));

        return http.build();
    }

    /** 401을 §7 에러 봉투로 내려준다 — 본문 없는 기본 응답을 대체한다. */
    private void writeUnauthenticated(
            HttpServletRequest request, HttpServletResponse response, ObjectMapper objectMapper)
            throws IOException {
        ApiError error = ApiError.of(ErrorCode.UNAUTHENTICATED, "인증이 필요합니다", null);
        log.warn("에러 봉투 401 UNAUTHENTICATED uri={} traceId={}",
                request.getRequestURI(), error.traceId());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(error));
    }
}
