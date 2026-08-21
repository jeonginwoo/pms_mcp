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
import kr.proten.pms.common.exception.StaleVersionException;
import kr.proten.pms.common.exception.ValidationException;

/**
 * 프로젝트 (PRD-pms §4).
 *
 * 상태 변경은 의도가 드러나는 메서드로만 한다 — setter가 없는 이유이며, 진척률
 * 100 저장이 상태를 바꾸지 않는다는 규칙(§5 자동 전이 폐지)도 그래서 지켜진다.
 * managerId는 대표 PM의 파생 읽기 필드이고 역할의 정본은 배정 레코드다(§4).
 */
@Entity
@Table(name = "projects")
public class Project {
    // 재개 시 되돌리는 진척률 (AC A7-3) — 완료의 전제가 100%였으므로 90은
    // "완료 직전으로 돌아간다"는 뜻이다
    private static final int REOPEN_PROGRESS = 90;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String client;
    @Column(nullable = false)
    private String name;
    // 중복 판정용 정규화 값 — 규칙은 ProjectKey가 소유한다 (AC A1-2)
    @Column(name = "normalized_client", nullable = false)
    private String normalizedClient;
    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;
    // 제품군
    @Column
    private String solution;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Engagement engagement;
    // 대표 PM — 배정의 role=PM 1행과 항상 일치해야 한다 (§4 불변식)
    @Column(name = "manager_id", nullable = false)
    private Long managerId;
    // 계약 관점 M/M — 배정 M/M(실투입 계획)과 다른 수치다 (상위 PRD §3)
    @Column(name = "contract_mm", nullable = false)
    private double contractMm;
    @Column(name = "start_date")
    private LocalDate startDate;
    @Column(name = "end_date")
    private LocalDate endDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;
    @Column(nullable = false)
    private int progress;
    // soft 삭제 — 목록·중복 검사에서 제외하고 과거 데이터는 보존한다
    @Column(nullable = false)
    private boolean deleted;
    @Version
    private long version;

    protected Project() {
    }

    private Project(
            ProjectKey key,
            String solution,
            Engagement engagement,
            Long managerId,
            double contractMm,
            LocalDate startDate,
            LocalDate endDate) {
        this.client = key.client();
        this.name = key.name();
        this.normalizedClient = key.normalizedClient();
        this.normalizedName = key.normalizedName();
        this.solution = solution;
        this.engagement = engagement;
        this.managerId = managerId;
        this.contractMm = contractMm;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = ProjectStatus.CONTRACT_PENDING;
        this.progress = 0;
        this.deleted = false;
    }

    /**
     * 새 프로젝트를 만든다 — 상태는 항상 계약대기, 진척률 0에서 시작한다 (AC A1-1).
     * 상태·진척률을 인자로 받지 않는 이유: 생성 시점의 상태는 선택이 아니라 규칙이다.
     */
    public static Project create(
            ProjectKey key,
            String solution,
            Engagement engagement,
            Long managerId,
            double contractMm,
            LocalDate startDate,
            LocalDate endDate) {
        if (managerId == null) {
            throw new ValidationException("PM은 필수입니다", "managerId");
        }

        requireValidPeriod(startDate, endDate);

        return new Project(key, solution, engagement, managerId, contractMm, startDate, endDate);
    }

    /**
     * 기간 규칙 (2026-08-22) — 종료일은 시작일보다 뒤여야 한다.
     * 한쪽만 비어 있는 것은 허용한다: 계약 전 단계에서 종료일이 아직 없을 수 있고,
     * 비교할 대상이 없는 값은 규칙 위반이 아니다.
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
     * 진척률을 갱신한다 (AC A2-2).
     * 100이 되어도 상태는 그대로다 — 완료 전이는 명시적 완료 처리만의 몫이다(§5).
     */
    public void updateProgress(int rate) {
        if (rate < 0 || rate > 100) {
            throw new ValidationException("진척률은 0에서 100 사이여야 합니다", "progress");
        }

        this.progress = rate;
    }

    /**
     * 정보를 수정한다 (AC A5-1) — 상태 전이는 {@link #advanceStatusTo}가 따로 맡는다.
     * managerId는 여기서 바꾸지 않는다: PM 교체는 배정 역할 이동을 동반하므로
     * 전용 경로(US-A6 `/pm`)만의 몫이고, 이 경로로 열면 PM 1행 불변식이 새어 나간다.
     */
    public void editInfo(
            ProjectKey key,
            String solution,
            Engagement engagement,
            double contractMm,
            LocalDate startDate,
            LocalDate endDate) {
        requireValidPeriod(startDate, endDate);
        this.client = key.client();
        this.name = key.name();
        this.normalizedClient = key.normalizedClient();
        this.normalizedName = key.normalizedName();
        this.solution = solution;
        this.engagement = engagement;
        this.contractMm = contractMm;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * 순방향으로 한 칸 전이한다 (AC A5-1·A5-2) — 같은 상태를 주면 아무 일도 없다.
     * 역방향·건너뛰기와 완료·유지보수중으로의 전이는 여기서 막는다: 완료·재개는
     * {@link #complete()}·{@link #reopen()}, 이관은 유지보수 전용 경로만이다(§5).
     */
    public void advanceStatusTo(ProjectStatus target) {
        if (target == status) {
            return;
        }

        if (!status.advancesTo(target)) {
            throw new ConflictException("INVALID_TRANSITION",
                    "%s에서 %s로는 바꿀 수 없습니다".formatted(status.label(), target.label()));
        }

        this.status = target;
    }

    /**
     * 완료 처리한다 (AC A7-1·A7-2·A7-4).
     * 진행중이 아닌 상태는 전이 위반이고, 진행중이어도 진척률 100%가 아니면 거절한다 —
     * 100% 저장이 상태를 바꾸지 않는 대신(§5) 완료의 전제로 남았다.
     */
    public void complete() {
        if (status != ProjectStatus.IN_PROGRESS) {
            throw new ConflictException("INVALID_TRANSITION",
                    "진행중인 프로젝트만 완료 처리할 수 있습니다 (현재 " + status.label() + ")");
        }

        if (progress != 100) {
            throw new ConflictException("PROGRESS_INCOMPLETE",
                    "진척률 100%에서만 완료 처리할 수 있습니다 (현재 " + progress + "%)");
        }

        this.status = ProjectStatus.COMPLETED;
    }

    /**
     * 재개한다 (AC A7-3·A7-4) — 유일하게 허용된 역방향 전이다.
     * 진척률을 90으로 되돌리는 이유: 100%는 완료의 전제라 재개 후에도 100%면
     * 곧바로 다시 완료 처리할 수 있어 "재개"가 무의미해진다. 이후 값은 US-A2로 고친다.
     * 유지보수중에서는 재개할 수 없다 — 이관된 계약과의 정합을 보호한다.
     */
    public void reopen() {
        if (status != ProjectStatus.COMPLETED) {
            throw new ConflictException("INVALID_TRANSITION",
                    "완료된 프로젝트만 재개할 수 있습니다 (현재 " + status.label() + ")");
        }

        this.status = ProjectStatus.IN_PROGRESS;
        this.progress = REOPEN_PROGRESS;
    }

    /**
     * soft 삭제 (AC A4-1) — 목록·중복 검사에서 빠지고 과거 데이터는 남는다.
     * 행을 지우지 않는 이유: 배정·감사 로그가 이 프로젝트를 가리키고 있다.
     */
    public void delete() {
        this.deleted = true;
    }

    /**
     * 대표 PM을 교체한다 (AC A6-1).
     * 역할의 정본은 배정 레코드이므로 이 필드만 바꾸면 불변식이 깨진다 —
     * 배정의 role 이동과 **같은 트랜잭션**에서만 호출해야 한다(A6-5).
     */
    public void changeManager(Long personId) {
        if (personId == null) {
            throw new ValidationException("PM은 필수입니다", "personId");
        }

        this.managerId = personId;
    }

    /**
     * 낙관적 락 검사 (AC A2-6·A8-7) — 최신 진척률·version을 함께 알려 클라이언트가
     * 무엇이 달라졌는지 알고 재시도할 수 있게 한다.
     */
    public void requireVersion(long expected) {
        if (version != expected) {
            throw new StaleVersionException(
                    "최신 진척률 " + progress + "%, version " + version);
        }
    }

    /**
     * 화면 그룹(phase) — status 파생값이며 유지보수중은 어디에도 들지 않아 null이다
     * (PRD-pms §5 · §7 단건 응답 파생 필드).
     */
    public ProjectPhase getPhase() {
        return switch (status) {
            case CONTRACT_PENDING, ORDER_CONFIRMED -> ProjectPhase.SALES;
            case IN_PROGRESS, COMPLETED -> ProjectPhase.SOLUTION;
            case UNDER_MAINTENANCE -> null;
        };
    }

    /** 완료 상태 여부 — 완료 상태의 진척률 직접 수정은 거절된다 (AC A2-8). */
    public boolean isCompleted() {
        return status == ProjectStatus.COMPLETED;
    }

    /** 완료 처리가 가능한 상태인가 — 진행중 + 진척률 100 (AC A7-1·A7-2). */
    public boolean isCompletable() {
        return completableAt(progress);
    }

    /**
     * 주어진 진척률로 저장했을 때 완료 처리가 가능해지는가 (AC A2-3 안내값).
     * 확인 단계에서 아직 저장하지 않은 값으로 물어야 하므로 진척률을 인자로 받는다.
     */
    public boolean completableAt(int candidateProgress) {
        return status == ProjectStatus.IN_PROGRESS && candidateProgress == 100;
    }

    public Long getId() {
        return id;
    }

    public String getClient() {
        return client;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedClient() {
        return normalizedClient;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getSolution() {
        return solution;
    }

    public Engagement getEngagement() {
        return engagement;
    }

    public Long getManagerId() {
        return managerId;
    }

    public double getContractMm() {
        return contractMm;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Project{id=" + id + ", client=" + client + ", name=" + name
                + ", status=" + status + ", progress=" + progress + "}";
    }
}
