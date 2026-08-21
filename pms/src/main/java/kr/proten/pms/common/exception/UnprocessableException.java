package kr.proten.pms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 422 — 애노테이션으로 표현할 수 없는 의미·규칙 위반 (PRD-pms §7:
 * REF_NOT_FOUND · PM_REQUIRED · MULTIPLE_PM · INVALID_ROLE 등).
 * code가 상황마다 다르므로 값으로 받는다 — 상황별 하위 클래스는 두지 않는다.
 */
public class UnprocessableException extends ApiException {
    public UnprocessableException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }
}
