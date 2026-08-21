package kr.proten.pms.mcp;

import java.util.List;

/**
 * 유지보수 이슈·코멘트 목록 (FR-AI-14) — id는 계약 또는 이슈 id.
 * matched: "CONTRACT"(계약 id — 소속 이슈 전체) | "ISSUE"(이슈 id — 해당 이슈만).
 */
public record MaintenanceLogsResult(
        String matched,
        int contractId,
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
