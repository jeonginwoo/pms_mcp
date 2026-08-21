package kr.proten.pms.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 인증이 꺼진 동안의 전면 허용 체인 (`pms.auth.enabled=false` — 기본값).
 *
 * 이 클래스가 필요한 이유: 스프링 시큐리티가 클래스패스에 있으면 기본이 전면 차단이라,
 * 인증을 "아직 안 쓰는" 상태가 곧 "아무 것도 못 쓰는" 상태가 된다. 로그인을 미리
 * 만들어 두려면(2026-08-21 결정) 이 체인이 짝으로 있어야 한다.
 *
 * **지우면 안 된다** — 지우는 순간 모든 요청이 401이 된다. 인증을 켤 때는 이 클래스를
 * 지우는 게 아니라 `pms.auth.enabled=true`로 스위치를 올려 person의 보호 체인이
 * 대신 활성화되게 한다.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "pms.auth.enabled", havingValue = "false", matchIfMissing = true)
class OpenSecurityConfig {

    @Bean
    SecurityFilterChain openSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());

        return http.build();
    }
}
