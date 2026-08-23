package kr.proten.pms.mcp.internal;

import kr.proten.pms.common.exception.ApiException;

/**
 * 도구 에러 (구현_노트 §2 — 예외→도구 에러 매핑). SDK가 isError:true + 메시지로 변환한다.
 * 메시지 = "[코드] 사용자 안내문"이며, **이 클래스가 그 문구의 정본**이다.
 *
 * 도메인 예외를 도구 문구로 바꾸는 판단도 여기 한 곳에 있다({@link #from}) —
 * conventions §4가 도구마다 제각기 변환하는 것을 금지하는 이유는, 같은 상황이 도구마다
 * 다른 문장으로 모델에게 도달하면 모델의 행동이 도구별로 갈라지기 때문이다.
 */
class ToolError extends RuntimeException {

    /**
     * 도메인 예외 → 도구 에러. `switch`에 default가 없는 것은 의도다: `ErrorCode`가
     * 늘어나면 여기서 컴파일이 깨지고, "이 상황을 모델에게 어떻게 말할지"를 그때
     * 결정하게 된다. 조용히 일반 문구로 흘리면 모델은 무엇을 다시 해야 하는지 모른다.
     */
    static ToolError from(ApiException exception) {
        return switch (exception.code()) {
            case NOT_FOUND -> notFound();
            case FORBIDDEN -> forbidden(exception.getMessage());
            case STALE_VERSION -> staleVersion(exception.getMessage());
            case PROJECT_COMPLETED -> projectCompleted();
            case DUPLICATE_NAME, DUPLICATE_EMAIL, DUPLICATE_ROOT, DUPLICATE_ASSIGNMENT,
                    IN_USE, INVALID_TRANSITION, NOT_IN_PROGRESS, PROGRESS_INCOMPLETE ->
                    conflict(exception.getMessage());
            case VALIDATION_ERROR, REF_NOT_FOUND, PM_REQUIRED, MULTIPLE_PM, INVALID_ROLE,
                    IMMUTABLE_ACCOUNT, IMMUTABLE_GROUP, IMMUTABLE_PERMISSION ->
                    validation(exception.getMessage());
            case NOT_IMPLEMENTED -> unavailable(exception.getMessage());
            // 보안 체인이 /mcp 전체를 막으므로 401은 도구까지 오지 않는다.
            // 내부 사정은 모델에게 알리지 않는다 — 재시도 판단만 준다.
            case UNAUTHENTICATED, INTERNAL_ERROR -> internal();
        };
    }

    /** 404 은닉 — 권한/부재 비구분 문구 (PRD-host S-4·FR-AI-20) */
    static ToolError notFound() {
        return new ToolError("[404 NOT_FOUND] 조회 가능한 범위에서 해당 데이터를 찾을 수 없습니다.");
    }

    static ToolError forbidden(String reason) {
        return new ToolError("[403 FORBIDDEN] 이 작업은 해당 권한이 있는 사용자만 가능합니다. " + reason);
    }

    static ToolError staleVersion(String latestValues) {
        return new ToolError("[409 STALE_VERSION] 다른 사용자가 먼저 수정했습니다. " + latestValues
                + " 자동 재시도하지 말고 최신값으로 사용자에게 다시 확인받아야 합니다.");
    }

    static ToolError projectCompleted() {
        return new ToolError("[409 PROJECT_COMPLETED] 완료 상태의 프로젝트는 진행률을 수정할 수 없습니다. "
                + "재개(화면에서만 가능) 후 수정할 수 있습니다.");
    }

    static ToolError conflict(String reason) {
        return new ToolError("[409 CONFLICT] " + reason);
    }

    static ToolError validation(String reason) {
        return new ToolError("[422 VALIDATION] " + reason);
    }

    /** 5xx/점검 — 오류 코드 + 안내문 + 재시도 가능 여부 (FR-AI-26 표준 형식) */
    static ToolError unavailable(String feature) {
        return new ToolError("[503 UNAVAILABLE] " + feature + " 기능은 아직 준비 중입니다. "
                + "지금은 재시도해도 결과를 받을 수 없습니다.");
    }

    static ToolError internal() {
        return new ToolError("[500 INTERNAL] 요청을 처리하지 못했습니다. 잠시 후 다시 시도할 수 있습니다.");
    }

    ToolError(String message) {
        super(message);
    }
}
