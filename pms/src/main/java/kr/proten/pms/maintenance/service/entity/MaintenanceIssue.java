package kr.proten.pms.maintenance.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.StaleVersionException;

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
        this.id = profile.id();
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

    /**
     * 낙관적 락 검사 (AC D3-2) — 최신 version을 알려 재조회 후 재시도하게 한다.
     * 계약·사이트와 같은 모양이다.
     */
    public void requireVersion(long expected) {
        if (version != expected) {
            throw new StaleVersionException("최신 이슈 version " + version);
        }
    }

    /**
     * 상태를 옮긴다 (AC D3-2) — 허용되지 않는 전이는 409 INVALID_TRANSITION이다
     * ({@code Project.advanceTo}와 같은 배치: 그래프는 열거가 알고 거절은 엔티티가 한다).
     *
     * <p><b>완료일은 상태가 정한다</b> — 호출자가 날짜를 고르게 하면 완료가 아닌 이슈에
     * 완료일이 남거나 그 반대가 된다. 재개하면 지운다: 재개된 이슈의 완료일은 사실이
     * 아니고, "언제 완료였다가 되돌아왔나"는 감사 이력이 답한다(그쪽이 정본이다).
     */
    public void changeStatus(IssueStatus target, LocalDate today) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException(ErrorCode.INVALID_TRANSITION,
                    "%s에서 %s로 바꿀 수 없습니다".formatted(status.label(), target.label()));
        }

        this.status = target;
        this.completedAt = target == IssueStatus.DONE ? today : null;
    }

    /**
     * 담당자를 바꾼다 (AC D3-2 재배정).
     *
     * <p>미배정으로 되돌리는 경로는 두지 않는다 — 호출자가 {@code null}을 주는 것은
     * "담당자를 건드리지 않는다"는 뜻이다({@code IssueEditCommand} 주석). 미배정은
     * 등록 시점의 상태이고(사이트에 담당 엔지니어가 없을 때) AC에 해제 요구가 없다.
     */
    public void reassign(long assigneeId) {
        this.assigneeId = assigneeId;
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
