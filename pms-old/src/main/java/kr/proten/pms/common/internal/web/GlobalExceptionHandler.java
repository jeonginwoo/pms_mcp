package kr.proten.pms.common.internal.web;

import kr.proten.pms.common.ApiException;
import kr.proten.pms.common.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 예외 → §7 에러 봉투 단일 변환 지점 (conventions/java-spring.md §4).
 * 401은 보안 체인(EntryPoint)이, 나머지는 이 핸들러가 담당한다.
 * 모든 봉투는 traceId와 함께 서버 로그에 기록한다 — 사용자가 보고한 traceId를
 * 로그 라인과 상관시킬 수 있어야 한다 (conventions §4 "traceId must trace").
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    // 봉투-로그 상관 기록용 (원문 토큰·개인정보 로그 금지 — conventions §6)
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 도메인·애플리케이션 예외 — 예외가 지닌 (상태, code) 그대로 봉투에 싣는다. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return respond(e.status(), ErrorResponse.of(e.code(), e.getMessage(), e.field()));
    }

    /** 입력 형식 오류(@Valid 실패) — §7 표의 400 VALIDATION_ERROR. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        var fieldError = e.getBindingResult().getFieldError();
        String field = fieldError == null ? null : fieldError.getField();
        String message = fieldError == null ? "입력 형식 오류" : fieldError.getDefaultMessage();

        return respond(HttpStatus.BAD_REQUEST,
                ErrorResponse.of("VALIDATION_ERROR", message, field));
    }

    /** 그 밖의 미분류 예외 — 내부 정보 누출 없이 500, 스택은 traceId와 함께 로그로. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        ErrorResponse envelope = ErrorResponse.of("INTERNAL_ERROR", "서버 내부 오류", null);
        log.error("에러 봉투 500 INTERNAL_ERROR traceId={}", envelope.error().traceId(), e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(envelope);
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatusCode status, ErrorResponse envelope) {
        log.warn("에러 봉투 {} {} field={} traceId={}", status.value(), envelope.error().code(),
                envelope.error().field(), envelope.error().traceId());

        return ResponseEntity.status(status).body(envelope);
    }
}
