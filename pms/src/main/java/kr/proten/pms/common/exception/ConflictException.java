package kr.proten.pms.common.exception;

/** 409 — 중복·상태 위반·사용 중 삭제 거절 (PRD-pms §7). */
public class ConflictException extends ApiException {
    public ConflictException(ErrorCode code, String message) {
        super(code, message);
    }
}
