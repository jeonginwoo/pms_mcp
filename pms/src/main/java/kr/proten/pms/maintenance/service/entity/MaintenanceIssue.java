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
    /**
     * 본문 (2026-08-26 신설) — null일 수 있다. 시드 267건은 본문 없이 적재됐고 그것이
     * 정상이다: 빈 문자열로 채우면 "안 쓴 것"과 "지운 것"이 같아진다.
     */
    private String content;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueStatus status;
    @Column(name = "assignee_id")
    private Long assigneeId;
    /**
     * 등록자 (2026-08-26 신설) — 수정·삭제 권한이 "등록자·담당자 + 계약 관리 플래그"라
     * 이 컬럼이 없으면 그 판정 자체가 불가능하다(전에는 담당자만 있었다).
     * 시드 이슈는 null이다 — 구 게시판이 작성자를 남기지 않았다.
     */
    @Column(name = "reporter_id")
    private Long reporterId;
    /**
     * soft 삭제 (2026-08-26) — 프로젝트 A4 선례. hard delete면 코멘트·감사가 가리키는
     * 대상이 사라진다.
     */
    @Column(nullable = false)
    private boolean deleted;
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
        this.content = profile.content();
        this.status = profile.status();
        this.assigneeId = profile.assigneeId();
        this.reporterId = profile.reporterId();
        this.receivedAt = profile.receivedAt();
        this.completedAt = profile.completedAt();
        this.deleted = false;
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

    /**
     * 제목·유형·본문을 고친다 (AC D3-5 — 2026-08-26 신설).
     *
     * <p>착수 계기가 이것이다: 등록 경로는 있는데 <b>제목 오타를 고칠 방법이 없었다</b>.
     *
     * <p><b>null은 "그대로"다</b> — 상태·담당 재배정({@code IssueEditCommand})이 이미 그
     * 규약을 쓰고 있어 한 요청에서 규약이 갈리지 않게 맞춘다. 그래서 <b>본문을 비우는
     * 것은 빈 문자열</b>로 표현한다: 세 칸 중 어느 것도 안 보낸 요청이 나머지를 지우면
     * 부분 수정이 불가능해진다.
     *
     * <p>사이트는 여기서 바꾸지 않는다 — 이슈가 어느 계약에 속하는지가 사이트에서
     * 파생되므로(계약 링크·전사 조회의 원천) 옮기는 것은 정정이 아니라 이동이고,
     * AC에 요구가 없다.
     */
    public void edit(IssueType type, String title, String content) {
        if (type != null) {
            this.type = type;
        }

        if (title != null) {
            this.title = title;
        }

        if (content != null) {
            // 빈 문자열은 "본문을 지운다"다 — null(그대로)과 구분해 null로 저장한다
            this.content = content.isBlank() ? null : content;
        }
    }

    /**
     * soft 삭제 (AC D3-6 — 2026-08-26 신설). 되돌리기 경로는 두지 않는다 —
     * AC에 요구가 없고, 프로젝트 삭제(A4)도 같다.
     *
     * <p>이미 삭제된 이슈는 조회 단계에서 404로 걸러지므로 여기 두 번 오지 않는다.
     */
    public void delete() {
        this.deleted = true;
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

    public String getContent() {
        return content;
    }

    public IssueStatus getStatus() {
        return status;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public boolean isDeleted() {
        return deleted;
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
