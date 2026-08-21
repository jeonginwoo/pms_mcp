package kr.proten.pms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 도메인·애플리케이션 예외의 공통 부모. PRD-pms §7 에러 표의 (HTTP 상태, code)를
 * 담고 common의 전역 핸들러가 에러 봉투로 변환한다 — 모듈별 임시 try-catch 금지
 * (conventions/java-spring.md §4).
 */
public abstract class ApiException extends RuntimeException {
    // §7 에러 표의 HTTP 상태
    private final HttpStatus status;
    // §7 에러 표의 code (예: NOT_FOUND, STALE_VERSION)
    private final String code;
    // 필드 단위 오류일 때만 채우는 필드명 (없으면 null)
    private final String field;

    protected ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    protected ApiException(HttpStatus status, String code, String message, String field) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String field() {
        return field;
    }
}
