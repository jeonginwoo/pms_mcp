package kr.proten.pms.maintenance.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 이슈 코멘트 — <b>작성자 본인만 수정·삭제한다</b> (AC D3-7, 2026-08-26 사용자 결정).
 *
 * <p><b>append-only 불변식은 폐기됐다</b>(구 D3-3 · 구 {@code MaintenanceLog} 계승분).
 * 원래 이 주석은 "수정·삭제 메서드를 두지 않고 모든 컬럼이 {@code updatable = false}다"
 * 였다. 폐기 근거는 사용자 결정이고, 함께 고른 것이 <b>범위를 작성자로 좁히는 것</b>이다
 * — 남의 이력을 고치는 길은 열지 않는다. tombstone(삭제 표시)안은 미채택이라 삭제는
 * 행을 지운다.
 *
 * <p><b>그래도 고쳐졌다는 사실은 남긴다</b>({@code updatedAt}): 수정 흔적까지 지우면
 * 이슈 이력이 "처음부터 이렇게 적혀 있었다"고 말하게 된다. 사용자 결정은 수정을
 * 허용한 것이고 흔적을 없애 달라는 것이 아니었다.
 *
 * <p>{@code version}은 여전히 없다. 코멘트는 한 사람(작성자)만 고칠 수 있어 두 화자가
 * 같은 행을 다툴 자리가 없다 — 낙관적 락이 막을 경합이 성립하지 않는다.
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
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    /** 마지막 수정 시각 — null이면 한 번도 고치지 않았다. */
    @Column(name = "updated_at")
    private Instant updatedAt;

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

    /**
     * 작성자 본인인가 (AC D3-7) — 판정을 엔티티가 답하는 이유는 이 규칙이 코멘트의
     * 성질이기 때문이다. 서비스가 {@code authorId}를 꺼내 비교하면 같은 비교가
     * 수정·삭제 두 자리에 생긴다.
     */
    public boolean isAuthoredBy(long personId) {
        return authorId != null && authorId == personId;
    }

    /** 내용을 고친다 (AC D3-7) — 고쳐졌다는 사실을 함께 남긴다. */
    public void rewrite(String content, Instant now) {
        this.content = content;
        this.updatedAt = now;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "IssueComment{id=" + id + ", issueId=" + issueId + "}";
    }
}
