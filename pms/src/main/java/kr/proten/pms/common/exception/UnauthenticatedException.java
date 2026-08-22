package kr.proten.pms.common.exception;

/** 401 — 토큰 없음·만료·사용 불가. 로그인 실패도 사유를 가르지 않고 여기로 수렴한다. */
public class UnauthenticatedException extends ApiException {
    public UnauthenticatedException(String message) {
        super(ErrorCode.UNAUTHENTICATED, message);
    }
}
