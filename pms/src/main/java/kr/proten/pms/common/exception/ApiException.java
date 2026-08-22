package kr.proten.pms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 도메인·애플리케이션 예외의 공통 부모. PRD-pms §7 에러 표의 code를 담고 common의
 * 전역 핸들러가 공통 응답 봉투로 변환한다 — 모듈별 임시 try-catch 금지
 * (conventions/java-spring.md §4).
 *
 * HTTP 상태는 예외가 따로 정하지 않고 `ErrorCode`에서 나온다 (2026-08-22) —
 * 코드와 상태가 두 곳에 있으면 어긋날 수 있다.
 */
public abstract class ApiException extends RuntimeException {
    // §7 에러 표의 code (상태를 함께 들고 있다)
    private final ErrorCode code;
    // 필드 단위 오류일 때만 채우는 필드명 (없으면 null)
    private final String field;

    protected ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    protected ApiException(ErrorCode code, String message, String field) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public HttpStatus status() {
        return code.status();
    }

    public ErrorCode code() {
        return code;
    }

    public String field() {
        return field;
    }
}
