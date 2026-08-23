package kr.proten.pms.mcp.internal.dto;

import java.util.List;

/**
 * 유지보수 이슈·코멘트 목록 (FR-AI-14) — id는 계약 또는 이슈 id.
 * matched: "CONTRACT"(계약 id — 소속 이슈 전체) | "ISSUE"(이슈 id — 해당 이슈만).
 *
 * <p>{@code contractId}·{@code contractName}은 <b>null일 수 있다</b>(2026-08-23 —
 * 실연결에서 정정): 이슈 id로 조회했고 그 이슈가 어느 계약에도 붙지 않은 경우다.
 * 시드 이슈 14건 중 7건이 그 상태다(태그가 프로젝트 고객사를 가리킨다 — 부록 B).
 * 전에는 {@code int}였는데, 그러면 미연결 이슈가 {@code contractId: 0}으로 나가
 * 모델이 그 값을 다른 도구에 넣는다 — 없는 것과 0번은 다른 말이다.
 */
public record MaintenanceLogsResult(
        String matched,
        Integer contractId,
        String contractName,
        List<IssueView> issues) {

    public record IssueView(
            int id,
            String type,
            String status,
            String title,
            String receivedAt,
            String assignee,
            List<CommentView> comments) {
    }

    public record CommentView(String date, String author, String text) {
    }
}
