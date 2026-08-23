package kr.proten.pms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * PRD-pms §7 에러 표 — code와 HTTP 상태의 유일한 정의 (2026-08-22 정리).
 *
 * 전에는 code가 호출 지점마다 문자열 리터럴이었다. 그러면 오타가 컴파일을 통과해
 * 런타임 응답으로 나가고, 같은 뜻의 코드가 두 이름으로 갈라져도 아무도 모른다.
 * 열거로 모으면 §7 표를 코드에서 한 번에 읽을 수 있고, 새 코드를 만들려면 여기
 * 한 줄을 추가하며 표와 맞는지 보게 된다.
 *
 * 상태를 함께 들고 있는 이유: "어떤 코드가 몇 번으로 나가는가"가 계약의 절반이고,
 * 예외 타입과 코드가 따로 상태를 정하면 둘이 어긋날 수 있다.
 */
public enum ErrorCode {
    // 400 — 입력 형식·범위
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),

    // 401 — 인증
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),

    // 403 — 권한 없는 행위
    FORBIDDEN(HttpStatus.FORBIDDEN),

    // 404 — 부재와 가시성 밖을 구분하지 않는다(은닉)
    NOT_FOUND(HttpStatus.NOT_FOUND),

    // 409 — 중복·상태 위반·사용 중
    DUPLICATE_NAME(HttpStatus.CONFLICT),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT),
    DUPLICATE_ROOT(HttpStatus.CONFLICT),
    DUPLICATE_ASSIGNMENT(HttpStatus.CONFLICT),
    IN_USE(HttpStatus.CONFLICT),
    INVALID_TRANSITION(HttpStatus.CONFLICT),
    NOT_IN_PROGRESS(HttpStatus.CONFLICT),
    PROJECT_COMPLETED(HttpStatus.CONFLICT),
    PROGRESS_INCOMPLETE(HttpStatus.CONFLICT),
    STALE_VERSION(HttpStatus.CONFLICT),

    // 422 — 참조 대상 없음·역할 구성 위반·고정 대상 변경 시도
    REF_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT),
    PM_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT),
    MULTIPLE_PM(HttpStatus.UNPROCESSABLE_CONTENT),
    INVALID_ROLE(HttpStatus.UNPROCESSABLE_CONTENT),
    IMMUTABLE_ACCOUNT(HttpStatus.UNPROCESSABLE_CONTENT),
    IMMUTABLE_GROUP(HttpStatus.UNPROCESSABLE_CONTENT),
    IMMUTABLE_PERMISSION(HttpStatus.UNPROCESSABLE_CONTENT),

    /**
     * 501 — 골격만 있고 로직이 없는 유스케이스 (2026-08-22 골격 확장).
     * §7 표에 없는 것은 의도다: 구현이 들어오면 던지는 자리가 사라진다.
     */
    NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED),

    // 500 — 분류되지 않은 예외. 내부 정보는 응답에 싣지 않는다
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
