package kr.proten.pms.common.exception;

/** 422 — 참조 대상 없음·역할 구성 위반·고정 대상 변경 시도 (PRD-pms §7). */
public class UnprocessableException extends ApiException {
    public UnprocessableException(ErrorCode code, String message) {
        super(code, message);
    }
}
