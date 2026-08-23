package kr.proten.pms.maintenance.service.entity;

import java.time.LocalDate;

/**
 * 이슈 생성 입력 묶음 (VO) — {@link MaintenanceIssue#of}가 받는다.
 * {@code Long siteId}·{@code Long assigneeId}와 {@code LocalDate} 두 개가 이어져
 * 인자 나열로는 순서가 바뀌어도 컴파일이 통과하므로 이름을 붙여 받는다.
 */
public record IssueProfile(
        Long siteId,
        IssueType type,
        String title,
        IssueStatus status,
        Long assigneeId,
        LocalDate receivedAt,
        LocalDate completedAt) {
}
