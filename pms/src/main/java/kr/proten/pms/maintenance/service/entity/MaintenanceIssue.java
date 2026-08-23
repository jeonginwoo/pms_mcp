package kr.proten.pms.maintenance.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;

/**
 * 유지보수 이슈 — 구 이슈 게시판의 대체 (PRD-pms US-D3).
 *
 * <p>{@code siteId}가 nullable인 것은 실데이터다: 시드 이슈 14건 중 7건의 태그가
 * 유지보수 사이트가 아니라 프로젝트 고객사를 가리켜 어느 계약에도 붙지 않는다
 * (부록 B — 미연결 실데이터 그대로 둠). 연결 기준은 계약명·계약사·사이트명 3종
 * 일치이고(2026-08-14 확정), 못 붙은 이슈를 버리면 원본 이력이 사라진다.
 *
 * <p>{@code assigneeId}는 등록 시 사이트의 담당 엔지니어에서 오고(D3-1) 재배정되면
 * 갈라진다 — 그래서 사이트를 되짚지 않고 자기 컬럼으로 갖는다.
 */
@Entity
@Table(name = "maintenance_issues")
public class MaintenanceIssue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "site_id")
    private Long siteId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueType type;
    @Column(nullable = false)
    private String title;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueStatus status;
    @Column(name = "assignee_id")
    private Long assigneeId;
    @Column(name = "received_at", nullable = false)
    private LocalDate receivedAt;
    @Column(name = "completed_at")
    private LocalDate completedAt;
    @Version
    private long version;

    protected MaintenanceIssue() {
    }

    private MaintenanceIssue(IssueProfile profile) {
        this.siteId = profile.siteId();
        this.type = profile.type();
        this.title = profile.title();
        this.status = profile.status();
        this.assigneeId = profile.assigneeId();
        this.receivedAt = profile.receivedAt();
        this.completedAt = profile.completedAt();
    }

    public static MaintenanceIssue of(IssueProfile profile) {
        return new MaintenanceIssue(profile);
    }

    public Long getId() {
        return id;
    }

    public Long getSiteId() {
        return siteId;
    }

    public IssueType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public IssueStatus getStatus() {
        return status;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public LocalDate getReceivedAt() {
        return receivedAt;
    }

    public LocalDate getCompletedAt() {
        return completedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "MaintenanceIssue{id=" + id + ", siteId=" + siteId + ", type=" + type + "}";
    }
}
