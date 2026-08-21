package kr.proten.pms.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 예외 → PRD-pms §7 에러 봉투 단일 변환 지점 (conventions/java-spring.md §4).
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

    /**
     * 본문을 읽을 수 없을 때 — §7 표의 400 VALIDATION_ERROR.
     * 파서 메시지를 그대로 내보내지 않는다: 타입·클래스 이름이 응답으로 새고,
     * 이 상황에서 호출자가 할 수 있는 일은 본문 형식을 고치는 것뿐이다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("요청 본문 파싱 실패: {}", e.getMessage());

        return respond(HttpStatus.BAD_REQUEST,
                ErrorResponse.of("VALIDATION_ERROR", "요청 본문을 읽을 수 없습니다", null));
    }

    /**
     * 매핑되지 않은 경로 — §7 표의 404다.
     *
     * 이 핸들러가 없으면 아래 catch-all이 스프링의 "핸들러 없음" 예외를 붙잡아
     * **오타 난 URL이 500으로 나간다**(2026-08-22 실기동에서 발견). 없는 경로와
     * 없는 리소스는 호출자에게 같은 사실이므로 같은 봉투로 답한다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        log.warn("매핑 없는 경로: {}", e.getResourcePath());

        return respond(HttpStatus.NOT_FOUND, ErrorResponse.of("NOT_FOUND", "해당 데이터 없음", null));
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
