package kr.proten.pms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 400 VALIDATION_ERROR — 입력 형식 위반 (PRD-pms §7).
 * 웹 계층이 생기면 대부분 jakarta validation(@Valid)이 대신 던지지만, 서비스가
 * 유일한 입구인 동안에는 형식 검증도 이 예외로 수렴시킨다(conventions §4).
 */
public class ValidationException extends ApiException {
    public ValidationException(String message, String field) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, field);
    }
}
