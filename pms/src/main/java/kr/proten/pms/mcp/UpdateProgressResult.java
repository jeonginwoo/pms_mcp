package kr.proten.pms.mcp;

/**
 * 진척률 변경 요약 또는 결과 (FR-AI-15 — 2단계 확인 프로토콜).
 * executed=false: 미실행·변경 요약만(확인 카드용). executed=true: 저장 완료.
 * completable: percent=100 — 상태 전이는 없으며 완료 처리는 별도(2026-08-06 완료 전이 재설계).
 */
public record UpdateProgressResult(
        boolean executed,
        int projectId,
        String projectName,
        int previousProgress,
        int requestedProgress,
        int version,
        boolean completable,
        String summary) {
}
