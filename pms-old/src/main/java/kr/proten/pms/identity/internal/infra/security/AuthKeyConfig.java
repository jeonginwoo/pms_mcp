package kr.proten.pms.identity.internal.infra.security;

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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT 서명 키·인코더 구성 — RS256+JWKS (구현_노트 §1-1·B-3 승격 경로 정합).
 * /mcp 어댑터 체인(MCP 담당)은 이 키의 JWKS 엔드포인트를 디코더로 소비할 수 있다.
 * REST 체인용 디코더는 ApiTokenVerification이 비-빈으로 보유 — JwtDecoder 빈은
 * /mcp 어댑터(McpJwtDecoderConfig)의 단일 소유라 여기서 빈으로 내지 않는다.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
class AuthKeyConfig {
    /**
     * 서명용 RSA 키 쌍.
     * ASSUMPTION: 개발 단계라 기동 시 임시 생성한다(재기동하면 기존 토큰 전부 무효 —
     * 재로그인). 운영 키 외부화(PEM 설정 주입)는 배포 구성 시 추가한다.
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
}
