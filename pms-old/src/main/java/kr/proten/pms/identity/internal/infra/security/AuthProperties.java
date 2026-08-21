package kr.proten.pms.identity.internal.infra.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 정책 설정 (§7 확정: access 1시간 · refresh 14일 — 미설정 시 그 값이 기본).
 */
@ConfigurationProperties("pms.auth")
public record AuthProperties(Duration accessTtl, Duration refreshTtl) {

    public AuthProperties {
        if (accessTtl == null) {
            accessTtl = Duration.ofHours(1);
        }

        if (refreshTtl == null) {
            refreshTtl = Duration.ofDays(14);
        }
    }
}
