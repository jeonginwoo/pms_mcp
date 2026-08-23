package kr.proten.pms.maintenance.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 이슈 코멘트 — <b>append-only</b> (PRD-pms D3-3, 구 {@code MaintenanceLog} 불변식 계승).
 *
 * <p>수정·삭제 메서드를 두지 않고 모든 컬럼이 {@code updatable = false}다. 보정은
 * 새 코멘트로만 한다 — 그래서 {@code version}도 없다: 낙관적 락은 수정이 있는
 * 엔티티의 장치이고, 여기에 두면 "고칠 수 있다"는 신호가 된다(감사 로그와 같은 원리).
 *
 * <p>시드에는 코멘트 본문이 없고 개수만 있어 적재는 0건이다. 그럼에도 지금 세우는
 * 이유는 MCP {@code list_maintenance_logs}의 port 계약이 {@code comments} 배열을
 * 요구하기 때문이다(2026-08-23 결정).
 */
@Entity
@Table(name = "issue_comments")
public class IssueComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "issue_id", nullable = false, updatable = false)
    private Long issueId;
    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String content;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IssueComment() {
    }

    private IssueComment(Long issueId, Long authorId, String content, Instant createdAt) {
        this.issueId = issueId;
        this.authorId = authorId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static IssueComment of(Long issueId, Long authorId, String content, Instant createdAt) {
        return new IssueComment(issueId, authorId, content, createdAt);
    }

    public Long getId() {
        return id;
    }

    public Long getIssueId() {
        return issueId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "IssueComment{id=" + id + ", issueId=" + issueId + "}";
    }
}
