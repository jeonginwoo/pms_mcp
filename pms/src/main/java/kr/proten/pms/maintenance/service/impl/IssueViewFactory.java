package kr.proten.pms.maintenance.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import kr.proten.pms.maintenance.service.dto.CommentView;
import kr.proten.pms.maintenance.service.dto.IssueView;
import kr.proten.pms.maintenance.service.entity.IssueComment;
import kr.proten.pms.maintenance.service.entity.MaintenanceIssue;
import kr.proten.pms.maintenance.service.entity.MaintenanceSite;
import kr.proten.pms.person.PersonRef;
import org.springframework.stereotype.Component;

/**
 * 이슈 엔티티 → 표현 변환.
 *
 * <p>담당자·코멘트 작성자를 <b>한 번에 모아</b> 참조로 바꾼다: 이슈 50건 × 코멘트마다
 * 인원을 되물으면 N+1이 모듈 경계를 넘는다.
 *
 * <p>사이트·계약이 null인 이슈를 버리지 않는다 — 시드 이슈 14건 중 7건이 그 상태이고
 * (부록 B: 미연결 실데이터 그대로 둠) 버리면 원본 이력이 사라진다.
 */
@Component
class IssueViewFactory {
    private final MaintenanceViewFactory personRefs;

    IssueViewFactory(MaintenanceViewFactory personRefs) {
        this.personRefs = personRefs;
    }

    List<IssueView> toViews(
            List<MaintenanceIssue> issues,
            Map<Long, MaintenanceSite> sites,
            Map<Long, String> contractNames,
            Map<Long, List<IssueComment>> commentsByIssue) {
        Map<Long, PersonRef> people = personRefs.refsOf(peopleIn(issues, commentsByIssue));

        return issues.stream()
                .map(issue -> toView(issue, sites, contractNames, commentsByIssue, people))
                .toList();
    }

    private IssueView toView(
            MaintenanceIssue issue,
            Map<Long, MaintenanceSite> sites,
            Map<Long, String> contractNames,
            Map<Long, List<IssueComment>> commentsByIssue,
            Map<Long, PersonRef> people) {
        MaintenanceSite site = issue.getSiteId() == null ? null : sites.get(issue.getSiteId());
        Long contractId = site == null ? null : site.getContractId();

        return new IssueView(
                issue.getId(),
                issue.getType().label(),
                issue.getStatus().label(),
                issue.getStatus(),
                issue.getTitle(),
                issue.getContent(),
                issue.getReporterId(),
                issue.getReceivedAt(),
                issue.getCompletedAt(),
                issue.getAssigneeId() == null ? null : people.get(issue.getAssigneeId()),
                issue.getSiteId(),
                site == null ? null : site.getName(),
                contractId,
                contractId == null ? null : contractNames.get(contractId),
                toCommentViews(commentsByIssue.getOrDefault(issue.getId(), List.of()), people),
                issue.getVersion());
    }

    private List<CommentView> toCommentViews(List<IssueComment> comments, Map<Long, PersonRef> people) {
        return comments.stream()
                .map(comment -> new CommentView(
                        comment.getId(),
                        people.get(comment.getAuthorId()),
                        comment.getContent(),
                        comment.getCreatedAt(),
                        comment.getUpdatedAt()))
                .toList();
    }

    /** 담당자 + 코멘트 작성자 — 한 번에 물을 인원 집합. */
    private static List<Long> peopleIn(
            List<MaintenanceIssue> issues, Map<Long, List<IssueComment>> commentsByIssue) {
        return java.util.stream.Stream.concat(
                        issues.stream().map(MaintenanceIssue::getAssigneeId),
                        commentsByIssue.values().stream()
                                .flatMap(List::stream)
                                .map(IssueComment::getAuthorId))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
