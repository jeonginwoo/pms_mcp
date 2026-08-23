package kr.proten.pms.maintenance.service.dto;

import java.time.LocalDate;
import java.util.List;
import kr.proten.pms.person.PersonRef;

/**
 * 이슈 표현 — MCP {@code list_maintenance_logs}의 {@code IssueView}와 같은 구성
 * (id·type·status·title·receivedAt·assignee·comments).
 *
 * <p>{@code site}·{@code contractName}이 null일 수 있다: 시드 이슈 14건 중 7건은
 * 태그가 유지보수 사이트가 아니라 프로젝트 고객사를 가리켜 어느 계약에도 붙지 않는다
 * (부록 B — 미연결 실데이터 그대로 둠). 화면·챗은 "계약 미연결 이슈"로 보여 준다.
 *
 * <p>{@code assignee}가 null이면 미배정이다 — D3-4의 미배정 필터가 찾는 상태다.
 */
public record IssueView(
        long id,
        String type,
        String status,
        String title,
        LocalDate receivedAt,
        LocalDate completedAt,
        PersonRef assignee,
        Long siteId,
        String siteName,
        Long contractId,
        String contractName,
        List<CommentView> comments,
        long version) {
}
