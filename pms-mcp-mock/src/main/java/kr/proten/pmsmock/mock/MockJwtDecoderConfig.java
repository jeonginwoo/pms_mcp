package kr.proten.pmsmock.mock;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
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
 * 목업 전용 HS256 대칭키 디코더 (구현_노트 부록 B-2).
 * 승격 시 withSecretKey → withJwkSetUri(JWKS)로만 교체 — 체인 구조·audience
 * 검증은 동일(§1-1·B-3 표). 대칭키는 로컬 실험용이라 실전 반입 금지.
 */
@Configuration
public class MockJwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder(@Value("${mock.jwt.secret}") String secret,
            OAuth2TokenValidator<Jwt> pmsAudienceValidator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), pmsAudienceValidator));
        return decoder;
    }
}
