package kr.proten.pms.auth.service.impl.token;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 자체 발급 JWT의 클레임 검증자 — 디코더에 조합으로 부착한다
 * (conventions/java-spring.md §4 "Token claim validation belongs to the decoder").
 * access(보호 체인)·refresh(회전) 디코더가 같은 검증자를 공유한다.
 */
final class TokenClaimValidators {
    private TokenClaimValidators() {
    }

    /** audience=pms 요구 (구조 원칙 4 — 다른 대상 토큰의 재사용 차단). */
    static OAuth2TokenValidator<Jwt> audiencePms() {
        return jwt -> jwt.getAudience().contains("pms")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "audience mismatch", null));
    }

    /** token_type 일치 요구 — access·refresh 교차 오용 차단. */
    static OAuth2TokenValidator<Jwt> tokenType(String expected) {
        return jwt -> expected.equals(jwt.getClaimAsString("token_type"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "not a " + expected + " token", null));
    }
}
