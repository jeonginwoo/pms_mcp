package kr.proten.pmsmock.model;

import java.util.List;

/** 유지보수 이슈 — type {장애,문의,요청} · 상태 {접수,처리중,고객확인대기,완료} · 코멘트는 append-only */
public record MaintenanceIssue(
        int id,
        int contractId,
        String type,
        String status,
        String title,
        String receivedAt,
        Integer assigneeId,
        List<IssueComment> comments) {
}
