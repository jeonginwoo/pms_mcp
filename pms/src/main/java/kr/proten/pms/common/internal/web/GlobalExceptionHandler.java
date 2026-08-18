package kr.proten.pms.common.internal.web;

import kr.proten.pms.common.ApiException;
import kr.proten.pms.common.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 예외 → §7 에러 봉투 단일 변환 지점 (conventions/java-spring.md §4).
 * 401은 보안 체인(EntryPoint)이, 나머지는 이 핸들러가 담당한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    // 예상 밖 오류 기록용 (원문 토큰·개인정보 로그 금지 — conventions §6)
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 도메인·애플리케이션 예외 — 예외가 지닌 (상태, code) 그대로 봉투에 싣는다. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return ResponseEntity.status(e.status())
                .body(ErrorResponse.of(e.code(), e.getMessage(), e.field()));
    }

    /** 입력 형식 오류(@Valid 실패) — §7 표의 400 VALIDATION_ERROR. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        var fieldError = e.getBindingResult().getFieldError();
        String field = fieldError == null ? null : fieldError.getField();
        String message = fieldError == null ? "입력 형식 오류" : fieldError.getDefaultMessage();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", message, field));
    }

    /** 그 밖의 미분류 예외 — 내부 정보 누출 없이 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("미분류 예외", e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "서버 내부 오류", null));
    }
}
