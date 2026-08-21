package kr.proten.pms.person.service.impl;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import java.time.Instant;
import java.util.List;
import kr.proten.pms.common.exception.UnauthenticatedException;
import kr.proten.pms.person.service.dto.IssuedTokens;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * TokenProvider의 Nimbus 구현 — sub=personId · aud=pms · token_type으로 access와
 * refresh를 구분한다. sub=personId 규약은 `/mcp` 어댑터가 화자를 해석하는 방식과 같다.
 */
@Component
class NimbusTokenProvider implements TokenProvider {
    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;
    // refresh 전용 디코더 — 공용 디코더는 token_type=access를 강제하므로 따로 만든다
    private final NimbusJwtDecoder refreshDecoder;

    NimbusTokenProvider(JwtEncoder jwtEncoder, AuthProperties properties, RSAKey rsaKey)
            throws JOSEException {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                TokenClaimValidators.audiencePms(),
                TokenClaimValidators.tokenType("refresh")));
        this.refreshDecoder = decoder;
    }

    @Override
    public IssuedTokens issue(Long personId) {
        Instant now = Instant.now();

        return new IssuedTokens(
                encode(personId, "access", now, now.plus(properties.accessTtl())),
                encode(personId, "refresh", now, now.plus(properties.refreshTtl())));
    }

    @Override
    public Long verifyRefresh(String refreshToken) {
        Jwt jwt = decodeOrReject(refreshToken);

        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new UnauthenticatedException("토큰을 사용할 수 없습니다");
        }
    }

    private Jwt decodeOrReject(String token) {
        try {
            return refreshDecoder.decode(token);
        } catch (JwtException e) {
            throw new UnauthenticatedException("토큰을 사용할 수 없습니다");
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

        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
}
