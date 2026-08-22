package kr.proten.pms.auth.service.impl.token;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 설정 (PRD-pms §7 확정: access 1시간 · refresh 14일 — 미설정 시 그 값이 기본).
 *
 * @param enabled 보호 체인 활성 여부 — false면 호출자를 헤더로 받는다(2026-08-21 결정)
 */
@ConfigurationProperties("pms.auth")
public record AuthProperties(boolean enabled, Duration accessTtl, Duration refreshTtl) {

    public AuthProperties {
        if (accessTtl == null) {
            accessTtl = Duration.ofHours(1);
        }

        if (refreshTtl == null) {
            refreshTtl = Duration.ofDays(14);
        }
    }
}
