package kr.proten.pms.identity.internal.infra.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * REST 보호 자원용 access 토큰 디코더 보유자 — 서명·만료에 더해 audience=pms
 * (구조 원칙 4)와 token_type=access를 요구한다(refresh로 API 호출 오용 차단).
 * JwtDecoder "빈"은 /mcp 어댑터(McpJwtDecoderConfig — MCP 담당)의 단일 소유라
 * 타입 충돌을 피하기 위해 빈이 아닌 보유 컴포넌트로 노출한다.
 */
@Component
public class ApiTokenVerification {
    // REST 체인이 명시 지정으로 쓰는 디코더
    private final JwtDecoder decoder;

    ApiTokenVerification(RSAKey rsaKey) throws JOSEException {
        NimbusJwtDecoder nimbusDecoder =
                NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        OAuth2TokenValidator<Jwt> audience = jwt ->
                jwt.getAudience().contains("pms")
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "audience mismatch", null));
        OAuth2TokenValidator<Jwt> accessType = jwt ->
                "access".equals(jwt.getClaimAsString("token_type"))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "not an access token", null));
        nimbusDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), audience, accessType));
        this.decoder = nimbusDecoder;
    }

    public JwtDecoder decoder() {
        return decoder;
    }
}
