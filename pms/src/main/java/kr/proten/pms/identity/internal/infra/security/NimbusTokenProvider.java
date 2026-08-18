package kr.proten.pms.identity.internal.infra.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import java.time.Instant;
import java.util.List;
import kr.proten.pms.identity.internal.application.InvalidTokenException;
import kr.proten.pms.identity.internal.application.IssuedTokens;
import kr.proten.pms.identity.internal.application.TokenProvider;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * TokenProvider의 Nimbus 구현 — sub=personId(목업 B2-2 정합) · aud=pms ·
 * token_type으로 access/refresh를 구분한다.
 */
@Component
class NimbusTokenProvider implements TokenProvider {
    // JWT 서명 인코더
    private final JwtEncoder jwtEncoder;
    // TTL 정책
    private final AuthProperties properties;
    // refresh 전용 디코더 — 공용 디코더는 token_type=access를 강제하므로 별도 생성
    private final NimbusJwtDecoder refreshDecoder;

    NimbusTokenProvider(JwtEncoder jwtEncoder, AuthProperties properties, RSAKey rsaKey)
            throws JOSEException {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.refreshDecoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
    }

    @Override
    public IssuedTokens issue(Long personId) {
        Instant now = Instant.now();
        String access = encode(personId, "access", now, now.plus(properties.accessTtl()));
        String refresh = encode(personId, "refresh", now, now.plus(properties.refreshTtl()));

        return new IssuedTokens(access, refresh);
    }

    @Override
    public Long verifyRefresh(String refreshToken) {
        Jwt jwt = decodeOrReject(refreshToken);

        if (!"refresh".equals(jwt.getClaimAsString("token_type"))) {
            throw new InvalidTokenException();
        }

        if (!jwt.getAudience().contains("pms")) {
            throw new InvalidTokenException();
        }

        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidTokenException();
        }
    }

    private Jwt decodeOrReject(String token) {
        try {
            return refreshDecoder.decode(token);
        } catch (JwtException e) {
            throw new InvalidTokenException();
        }
    }

    private String encode(Long personId, String tokenType, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(personId))
                .audience(List.of("pms"))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("token_type", tokenType)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
