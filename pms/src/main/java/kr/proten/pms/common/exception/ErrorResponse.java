package kr.proten.pms.common.exception;

import java.util.UUID;

/**
 * PRD-pms §7 에러 봉투 — 모든 에러 응답은 {error:{code,message,field,traceId}} 형태.
 */
public record ErrorResponse(ErrorBody error) {

    /** 봉투 내부 본문. */
    public record ErrorBody(String code, String message, String field, String traceId) {
    }

    public static ErrorResponse of(String code, String message, String field) {
        return new ErrorResponse(new ErrorBody(code, message, field, UUID.randomUUID().toString()));
    }
}
