package kr.proten.pms.mcp;

/**
 * 도구 에러 (구현_노트 §2 — 예외→도구 에러 매핑).
 * 메시지 = "[코드] 사용자 안내문" — 문구 정본은 구현_노트 §2 표.
 * SDK가 isError:true + 메시지로 변환한다.
 */
public class ToolError extends RuntimeException {

    /** 404 은닉 — 권한/부재 비구분 문구 (PRD-host S-4·FR-AI-20) */
    public static ToolError notFound() {
        return new ToolError("[404 NOT_FOUND] 조회 가능한 범위에서 해당 데이터를 찾을 수 없습니다.");
    }

    public static ToolError forbidden(String reason) {
        return new ToolError("[403 FORBIDDEN] 이 작업은 해당 권한이 있는 사용자만 가능합니다. " + reason);
    }

    public static ToolError staleVersion(int latestProgress, int latestVersion) {
        return new ToolError("[409 STALE_VERSION] 다른 사용자가 먼저 수정했습니다. 최신값은 "
                + latestProgress + "%입니다. (version: " + latestVersion + ")");
    }

    public static ToolError projectCompleted() {
        return new ToolError("[409 PROJECT_COMPLETED] 완료 상태의 프로젝트는 진행률을 수정할 수 없습니다. "
                + "재개(화면에서만 가능) 후 수정할 수 있습니다.");
    }

    public static ToolError validation(String reason) {
        return new ToolError("[422 VALIDATION] " + reason);
    }

    /** 5xx/점검 — 오류 코드 + 안내문 + 재시도 가능 여부 (FR-AI-26 표준 형식) */
    public static ToolError unavailable(String feature) {
        return new ToolError("[503 UNAVAILABLE] " + feature + " 기능은 아직 준비 중입니다. "
                + "지금은 재시도해도 결과를 받을 수 없습니다.");
    }

    public ToolError(String message) {
        super(message);
    }
}
