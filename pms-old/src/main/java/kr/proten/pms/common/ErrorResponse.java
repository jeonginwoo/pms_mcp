package kr.proten.pms.common;

import java.util.UUID;

/**
 * §7 에러 봉투 — 모든 에러 응답은 {error:{code,message,field,traceId}} 형태.
 * REST와 /mcp 어댑터가 같은 봉투를 쓴다.
 */
public record ErrorResponse(ErrorBody error) {

    /** 봉투 내부 본문. */
    public record ErrorBody(String code, String message, String field, String traceId) {
    }

    public static ErrorResponse of(String code, String message, String field) {
        return new ErrorResponse(new ErrorBody(code, message, field, UUID.randomUUID().toString()));
    }
}
