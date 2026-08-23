package kr.proten.pms.auth.service.impl.token;

import kr.proten.pms.auth.service.dto.IssuedTokens;

/**
 * JWT 발급·refresh 검증 — 서명 방식을 유스케이스에서 떼어 놓는 지점.
 */
public interface TokenProvider {

    /** access·refresh 쌍을 발급한다. sub = personId. */
    IssuedTokens issue(Long personId);

    /** refresh 토큰을 검증하고 personId를 돌려준다. 실패는 401로 수렴한다. */
    Long verifyRefresh(String refreshToken);
}
