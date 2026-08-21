package kr.proten.pms.identity.internal.application;

import kr.proten.pms.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * refresh 토큰 검증 실패 — 서명·만료·audience·token_type 불일치 전부 같은 응답
 * (§7: UNAUTHENTICATED 401, 실패 원인 비노출).
 */
public class InvalidTokenException extends ApiException {
    public InvalidTokenException() {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "토큰이 유효하지 않습니다");
    }
}
