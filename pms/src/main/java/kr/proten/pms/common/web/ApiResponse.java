package kr.proten.pms.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 응답 본문의 공통 형태 (2026-08-22 결정) — `{success, data}` 또는 `{success, error}`.
 *
 * 봉투를 하나로 둔 이유: 이전에는 성공이 원본 그대로였고 실패만 봉투였다. 그러면
 * 호출자가 응답 형태를 상태 코드로 먼저 갈라야 하고, 화면·`/mcp` 어댑터·테스트가
 * 각자 그 분기를 다시 만든다. 형태가 하나면 "먼저 success를 보고 data나 error를
 * 읽는다"가 유일한 규칙이 된다.
 *
 * 목록은 `data`에 §7 page 봉투(`PageResponse`)가 그대로 들어간다 — 봉투가 두 겹인
 * 것은 의미가 다르기 때문이다: 바깥은 성공/실패, 안쪽은 페이지 메타.
 *
 * null 필드는 직렬화에서 빠진다 — 성공 응답에 빈 `error`가, 실패 응답에 빈 `data`가
 * 붙어 있으면 읽는 쪽이 그 존재를 판정 조건으로 착각한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    /** 성공 — 본문이 없는 행위(삭제·읽음 처리)는 data가 null이다. */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 성공했지만 돌려줄 값이 없는 경우. */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> fail(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
