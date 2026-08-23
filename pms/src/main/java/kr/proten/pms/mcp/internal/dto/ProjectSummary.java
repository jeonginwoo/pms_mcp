package kr.proten.pms.mcp.internal.dto;

/** AI 응답용 얇은 DTO (구현_노트 §5 — 화면 DTO 재사용 금지) */
public record ProjectSummary(
        int id,
        String name,
        String client,
        String status,
        int progress,
        String startDate,
        String endDate,
        String team,
        String division) {
}
