package kr.proten.pms.identity.internal.application;

/**
 * JWT 발급·refresh 검증 포트 — 구현은 infra(RS256+JWKS, 구현_노트 §1-1 정합).
 * 클레임 계약: sub=personId(문자열 — 목업 B2-2와 동일) · aud=pms · token_type=access|refresh.
 */
public interface TokenProvider {
    /** access(1시간)+refresh(14일) 쌍을 발급한다 — TTL은 §7 JWT 정책. */
    IssuedTokens issue(Long personId);

    /**
     * refresh 토큰을 검증하고 personId를 돌려준다.
     * 서명·만료·audience·token_type 중 하나라도 어긋나면 InvalidTokenException.
     */
    Long verifyRefresh(String refreshToken);
}
