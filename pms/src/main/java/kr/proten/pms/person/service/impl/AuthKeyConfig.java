package kr.proten.pms.person.service.impl;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT 서명 키·인코더·디코더 구성 — RS256 + JWKS.
 *
 * 보호 체인(controller의 ApiSecurityConfig)은 여기서 내는 `JwtDecoder` 빈을 타입으로
 * 주입받는다 — 빈 주입이라 컨트롤러가 이 패키지를 import하지 않는다(계층 방향 유지).
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
class AuthKeyConfig {

    /**
     * 서명용 RSA 키 쌍.
     * ASSUMPTION: 개발 단계라 기동 시 임시 생성한다 — 재기동하면 기존 토큰이 전부
     * 무효가 되어 재로그인이 필요하다. 운영 키 외부화(PEM 주입)는 배포 구성 시 추가한다.
     */
    @Bean
    RSAKey rsaKey() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey rsaKey) {
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));

        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * 보호 자원용 access 토큰 디코더 — 서명·만료에 더해 audience=pms(구조 원칙 4)와
     * token_type=access를 요구한다(refresh로 API를 호출하는 오용 차단).
     */
    @Bean
    JwtDecoder accessTokenDecoder(RSAKey rsaKey) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                TokenClaimValidators.audiencePms(),
                TokenClaimValidators.tokenType("access")));

        return decoder;
    }
}
