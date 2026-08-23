package kr.proten.pms.common.exception;

/** 403 — 권한 없는 행위. 조회 권한 부족은 403이 아니라 404다(은닉 — NotFoundException). */
public class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
