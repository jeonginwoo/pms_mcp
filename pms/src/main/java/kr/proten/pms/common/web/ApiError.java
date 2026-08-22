package kr.proten.pms.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import kr.proten.pms.common.exception.ErrorCode;

/**
 * 실패 응답의 본문 (PRD-pms §7 — code·message·field·traceId).
 *
 * `traceId`는 여기서 만든다: 사용자가 보고할 수 있는 식별자와 서버 로그가 같은
 * 값을 갖도록 봉투를 만드는 지점이 하나여야 한다(conventions §4 "traceId must trace").
 *
 * @param field 필드 단위 오류일 때만 채운다 — 없으면 직렬화에서 빠진다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, String field, String traceId) {

    public static ApiError of(ErrorCode code, String message, String field) {
        return new ApiError(code.name(), message, field, UUID.randomUUID().toString());
    }
}
