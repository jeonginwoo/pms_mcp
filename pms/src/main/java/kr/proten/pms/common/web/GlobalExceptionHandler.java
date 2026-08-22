package kr.proten.pms.common.web;

import kr.proten.pms.common.exception.ApiException;
import kr.proten.pms.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 예외 → 공통 응답 봉투(`ApiResponse.fail`) 단일 변환 지점
 * (conventions/java-spring.md §4).
 *
 * 상태 코드는 예외가 아니라 `ErrorCode`에서 나온다 — 코드와 상태가 어긋나지 않는다.
 * 모든 봉투는 traceId와 함께 서버 로그에 기록한다 — 사용자가 보고한 traceId를
 * 로그 라인과 상관시킬 수 있어야 한다 (conventions §4 "traceId must trace").
 *
 * 이 클래스가 `common/web`에 있는 이유(2026-08-22): 예외 타입이 아니라 **예외를 응답으로
 * 바꾸는 웹 어댑터**라서 `ApiResponse`·`ApiError` 옆이 맞는 자리다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    // 봉투-로그 상관 기록용 (원문 토큰·개인정보 로그 금지 — conventions §6)
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 도메인·애플리케이션 예외 — 예외가 지닌 ErrorCode 그대로 봉투에 싣는다. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException e) {
        return respond(e.code(), e.getMessage(), e.field());
    }

    /** 입력 형식 오류(@Valid 실패) — §7 표의 400 VALIDATION_ERROR. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        var fieldError = e.getBindingResult().getFieldError();
        String field = fieldError == null ? null : fieldError.getField();
        String message = fieldError == null ? "입력 형식 오류" : fieldError.getDefaultMessage();

        return respond(ErrorCode.VALIDATION_ERROR, message, field);
    }

    /**
     * 본문을 읽을 수 없을 때 — §7 표의 400 VALIDATION_ERROR.
     * 파서 메시지를 그대로 내보내지 않는다: 타입·클래스 이름이 응답으로 새고,
     * 이 상황에서 호출자가 할 수 있는 일은 본문 형식을 고치는 것뿐이다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(
            HttpMessageNotReadableException e) {
        log.warn("요청 본문 파싱 실패: {}", e.getMessage());

        return respond(ErrorCode.VALIDATION_ERROR, "요청 본문을 읽을 수 없습니다", null);
    }

    /**
     * 필수 요청 파라미터 누락 — §7 표의 400 VALIDATION_ERROR.
     * 어떤 파라미터가 빠졌는지는 알려 준다(값이 아니라 이름이라 노출 위험이 없다).
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException e) {
        return respond(ErrorCode.VALIDATION_ERROR, "필수 요청 파라미터가 없습니다",
                e.getParameterName());
    }

    /**
     * 파라미터·경로 변수의 타입 불일치 — §7 표의 400 VALIDATION_ERROR.
     *
     * 이 핸들러가 없으면 아래 catch-all이 붙잡아 **`?month=2026-8-1`이나
     * `/api/projects/abc` 같은 요청이 500으로 나간다**(2026-08-22 발견 — 매핑 없는
     * 경로가 500이던 것과 같은 계열의 구멍이다). 호출자가 고칠 수 있는 입력 오류를
     * 서버 장애로 알리면 원인 추적이 엉뚱한 곳으로 간다.
     * 기대 타입은 응답에 싣지 않는다 — 내부 클래스 이름이 새는 경로다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        log.warn("요청 값 타입 불일치: name={}", e.getName());

        return respond(ErrorCode.VALIDATION_ERROR, "요청 값의 형식이 올바르지 않습니다", e.getName());
    }

    /**
     * 매핑되지 않은 경로 — §7 표의 404다.
     *
     * 이 핸들러가 없으면 아래 catch-all이 스프링의 "핸들러 없음" 예외를 붙잡아
     * **오타 난 URL이 500으로 나간다**(2026-08-22 실기동에서 발견). 없는 경로와
     * 없는 리소스는 호출자에게 같은 사실이므로 같은 봉투로 답한다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        log.warn("매핑 없는 경로: {}", e.getResourcePath());

        return respond(ErrorCode.NOT_FOUND, "해당 데이터 없음", null);
    }

    /** 그 밖의 미분류 예외 — 내부 정보 누출 없이 500, 스택은 traceId와 함께 로그로. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        ApiError error = ApiError.of(ErrorCode.INTERNAL_ERROR, "서버 내부 오류", null);
        log.error("에러 봉투 500 INTERNAL_ERROR traceId={}", error.traceId(), e);

        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.fail(error));
    }

    private ResponseEntity<ApiResponse<Void>> respond(
            ErrorCode code, String message, String field) {
        ApiError error = ApiError.of(code, message, field);
        HttpStatusCode status = code.status();
        logEnvelope(code, status, error);

        return ResponseEntity.status(status).body(ApiResponse.fail(error));
    }

    /**
     * 봉투를 traceId와 함께 남긴다.
     *
     * 골격 응답(501)만 INFO로 낮춘다: 아직 없는 기능을 부르는 것은 이 단계의 **정상
     * 트래픽**이라, WARN으로 남기면 권한 거절·입력 오류와 심각도가 구분되지 않아
     * 경보의 신호가 죽는다. 구현이 들어와 501이 사라지면 이 분기도 함께 사라진다.
     */
    private void logEnvelope(ErrorCode code, HttpStatusCode status, ApiError error) {
        if (code == ErrorCode.NOT_IMPLEMENTED) {
            log.info("에러 봉투 {} {} traceId={}", status.value(), error.code(), error.traceId());

            return;
        }

        log.warn("에러 봉투 {} {} field={} traceId={}", status.value(), error.code(),
                error.field(), error.traceId());
    }
}
