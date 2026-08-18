package kr.proten.pms.mcp.internal;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * JwtDecoder 공급 (구현_노트 §1-1·B-3 표).
 * JWKS(pms.auth.jwks-uri)가 설정되면 항상 그쪽이 이긴다 — HS256은 실제 발급
 * 체계가 없는 동안(게이트 M0)의 로컬 검증용이며 운영 반입 금지.
 * audience 검증(aud=pms)은 어느 디코더든 동일하게 조합한다.
 */
@Configuration
@EnableConfigurationProperties(PmsAuthProperties.class)
public class McpJwtDecoderConfig {

    private static final Logger log = LoggerFactory.getLogger(McpJwtDecoderConfig.class);

    @Bean
    JwtDecoder jwtDecoder(PmsAuthProperties props, OAuth2TokenValidator<Jwt> pmsAudienceValidator) {
        NimbusJwtDecoder decoder;
        if (props.jwksUri() != null && !props.jwksUri().isBlank()) {
            decoder = NimbusJwtDecoder.withJwkSetUri(props.jwksUri()).build();
        } else if (props.hs256Secret() != null && !props.hs256Secret().isBlank()) {
            log.warn("/mcp 토큰 검증이 로컬 HS256 디코더로 동작 중 — 운영 금지 (pms.auth.jwks-uri 미설정)");
            decoder = NimbusJwtDecoder
                    .withSecretKey(new SecretKeySpec(
                            props.hs256Secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build();
        } else {
            throw new IllegalStateException(
                    "pms.auth.jwks-uri 또는 pms.auth.hs256-secret 중 하나는 설정해야 합니다 — /mcp 토큰 검증 불가");
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), pmsAudienceValidator));
        return decoder;
    }
}
