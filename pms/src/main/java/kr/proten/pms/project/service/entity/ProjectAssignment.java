package kr.proten.pms.project.service.entity;

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
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.StaleVersionException;
import kr.proten.pms.common.exception.ValidationException;

/**
 * 프로젝트 배정 — 프로젝트 역할의 정본 (PRD-pms §4 · 상위 PRD §4-2).
 *
 * projectId·personId를 객체 참조가 아니라 id로 갖는다: personId는 모듈 경계를
 * 넘는 참조라 규칙상 id뿐이고(§0), projectId도 같은 형태로 맞춰 배정 목록 질의가
 * 프로젝트 로딩에 매달리지 않게 한다.
 */
@Entity
@Table(name = "project_assignments")
public class ProjectAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    @Column(name = "person_id", nullable = false)
    private Long personId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectRole role;
    @Column(name = "start_date")
    private LocalDate startDate;
    @Column(name = "end_date")
    private LocalDate endDate;
    // 실투입 계획 M/M — 계약 배분 숫자가 아니다 (상위 PRD §3 · AC B1-5)
    @Column(name = "monthly_mm", nullable = false)
    private double monthlyMm;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status;
    @Version
    private long version;

    protected ProjectAssignment() {
    }

    private ProjectAssignment(
            Long projectId,
            Long personId,
            ProjectRole role,
            LocalDate startDate,
            LocalDate endDate,
            double monthlyMm) {
        this.projectId = projectId;
        this.personId = personId;
        this.role = role;
        this.startDate = startDate;
        this.endDate = endDate;
        this.monthlyMm = monthlyMm;
        this.status = AssignmentStatus.ACTIVE;
    }

    /** 배정을 만든다 — 새 배정은 항상 진행 상태다. */
    public static ProjectAssignment of(
            Long projectId,
            Long personId,
            ProjectRole role,
            LocalDate startDate,
            LocalDate endDate,
            double monthlyMm) {
        requireValidPeriod(startDate, endDate);

        return new ProjectAssignment(projectId, personId, role, startDate, endDate, monthlyMm);
    }

    /**
     * 기간 규칙 (2026-08-22) — 프로젝트와 같은 규칙이다: 종료일은 시작일보다 뒤여야 한다.
     * 한쪽만 비어 있는 것은 허용한다(미지정은 프로젝트 기간으로 채워진다 — A6-6).
     */
    private static void requireValidPeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return;
        }

        if (!endDate.isAfter(startDate)) {
            throw new ValidationException("종료일은 시작일보다 뒤여야 합니다", "endDate");
        }
    }

    /**
     * 기간·투입 M/M을 수정한다 (AC B1-4).
     * 역할은 여기서 바꾸지 않는다 — 역할 지정·해제는 전용 경로(US-A6 `/roles`)만의
     * 몫이고, 이 경로로 열면 PM 1행 불변식을 우회할 수 있다(A6-7).
     */
    public void reschedule(LocalDate startDate, LocalDate endDate, double monthlyMm) {
        requireValidPeriod(startDate, endDate);
        this.startDate = startDate;
        this.endDate = endDate;
        this.monthlyMm = monthlyMm;
    }

    /**
     * 배정을 종료한다 (AC B2-1) — 종료월 이후 가동률 모집단에서 빠진다.
     * 행은 남긴다: 지난 달의 가동률은 그때의 배정으로 계산되어야 한다.
     * 이미 종료된 배정을 다시 종료하는 요청은 충돌로 알린다 — 종료는 상태 전이라
     * 조용히 넘기면 PM이 "종료했다고 생각한 배정"을 확인할 방법이 없다.
     */
    public void close() {
        if (status == AssignmentStatus.CLOSED) {
            throw new ConflictException(ErrorCode.INVALID_TRANSITION, "이미 종료된 배정입니다");
        }

        this.status = AssignmentStatus.CLOSED;
    }

    /**
     * 역할을 바꾼다 (AC A6-1·A6-3).
     * PM 1행 불변식(A6-5)은 이 메서드가 지킬 수 없다 — 프로젝트 전체를 봐야 하는
     * 규칙이라, PM 이동은 반드시 직전 PM 강등과 같은 트랜잭션에서 함께 일어난다.
     */
    public void changeRole(ProjectRole role) {
        this.role = role;
    }

    /** 낙관적 락 검사 (AC B1-4) — 최신 version을 알려 재조회 후 재시도하게 한다. */
    public void requireVersion(long expected) {
        if (version != expected) {
            throw new StaleVersionException("최신 배정 version " + version);
        }
    }

    /** PM 배정인가 — 종료·역할 변경 시 PM 1행 불변식(A6-5)을 지키는 판정. */
    public boolean isManager() {
        return role == ProjectRole.PM;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getPersonId() {
        return personId;
    }

    public ProjectRole getRole() {
        return role;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public double getMonthlyMm() {
        return monthlyMm;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "ProjectAssignment{id=" + id + ", projectId=" + projectId
                + ", personId=" + personId + ", role=" + role + "}";
    }
}
