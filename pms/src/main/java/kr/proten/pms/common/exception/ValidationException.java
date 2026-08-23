package kr.proten.pms.common.exception;

/** 400 — 입력 형식·범위 위반. 어느 필드인지 함께 싣는다 (PRD-pms §7). */
public class ValidationException extends ApiException {
    public ValidationException(String message, String field) {
        super(ErrorCode.VALIDATION_ERROR, message, field);
    }
}
