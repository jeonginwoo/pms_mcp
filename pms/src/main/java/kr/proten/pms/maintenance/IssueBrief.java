package kr.proten.pms.maintenance;

import java.time.LocalDate;
import java.util.List;

/**
 * 이슈 한 건 — MCP {@code IssueView}가 채워지는 모양이다.
 *
 * <p>{@code assignee}가 null이면 미배정이다(신규 예정·종료 사이트의 이슈).
 * {@code siteName}이 null이면 어느 계약에도 붙지 않은 이슈다 — 시드 14건 중 7건이
 * 그 상태이고 버리지 않는다(부록 B "미연결 실데이터 그대로 둠").
 */
public record IssueBrief(
        long id,
        String type,
        String status,
        String title,
        LocalDate receivedAt,
        String assignee,
        String siteName,
        List<CommentBrief> comments) {
}
