package kr.proten.pms.maintenance.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import kr.proten.pms.common.exception.StaleVersionException;

/**
 * 유지보수 계약 (PRD-pms §4 — 계약/사이트/이슈 3층의 최상위).
 *
 * <p>사이트를 객체로 물지 않는다: 계약 하나에 45사이트가 달린 실데이터가 있어
 * (가온아이) 목록 질의가 사이트 로딩에 매달리면 계약 검색이 그만큼 무거워진다.
 * 사이트는 {@code contractId}로 따로 읽는다 — project가 배정을 다루는 방식과 같다.
 *
 * <p>{@code sourceProjectId}가 프로젝트와의 유일한 연결이고 nullable이다: 이관
 * 경로(US-D1)면 1:1로 채워지고, OEM 채널처럼 원천 프로젝트가 없는 직접 등록
 * (US-D2)이면 비어 있다. 모듈 간 연결은 id로만 한다(§0).
 */
@Entity
@Table(name = "maintenance_contracts")
public class MaintenanceContract {
    @Id
    private Long id;
    @Column(name = "source_project_id")
    private Long sourceProjectId;
    @Column(nullable = false)
    private String contractor;
    @Column(nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status;
    // 원본 시트 섹션 — status에서 파생되지 않는다(교차 실측)
    @Column(name = "sheet_section")
    private String sheetSection;
    @Column(name = "contract_date")
    private LocalDate contractDate;
    // 시트의 비날짜 계약일("진행중" 등) 원문 (부록 B)
    @Column(name = "contract_date_note")
    private String contractDateNote;
    @Column(name = "start_date")
    private LocalDate startDate;
    @Column(name = "end_date")
    private LocalDate endDate;
    private Long amount;
    @Column(name = "monthly_amount")
    private Long monthlyAmount;
    @Column(name = "sales_rep_id")
    private Long salesRepId;
    // 대분류(검색엔진|인프라)
    private String category;
    // 라이선스·제품 사양("검색엔진 3Copy+추천모듈") — 상거래 조건이라 계약 레벨이다
    @Column(name = "target_infra")
    private String targetInfra;
    @Column(name = "regular_check")
    private String regularCheck;
    @Column(columnDefinition = "text")
    private String note;
    @Version
    private long version;

    protected MaintenanceContract() {
    }

    private MaintenanceContract(ContractProfile profile) {
        this.id = profile.id();
        this.sourceProjectId = profile.sourceProjectId();
        this.contractor = profile.contractor();
        this.name = profile.name();
        this.status = profile.status();
        this.sheetSection = profile.sheetSection();
        this.contractDate = profile.contractDate();
        this.contractDateNote = profile.contractDateNote();
        this.startDate = profile.startDate();
        this.endDate = profile.endDate();
        this.amount = profile.amount();
        this.monthlyAmount = profile.monthlyAmount();
        this.salesRepId = profile.salesRepId();
        this.category = profile.category();
        this.targetInfra = profile.targetInfra();
        this.regularCheck = profile.regularCheck();
        this.note = profile.note();
    }

    /**
     * 계약을 만든다. 필드가 많아 {@link ContractProfile}로 묶어 받는다 — 인자
     * 15개를 나열하면 호출 지점에서 순서가 바뀌어도 컴파일이 통과한다.
     */
    public static MaintenanceContract of(ContractProfile profile) {
        return new MaintenanceContract(profile);
    }

    /**
     * 계약 정보를 고친다 (AC D2-2) — 시트 유래 필드와 원천 프로젝트 연결은 남는다
     * ({@link ContractEdit} 주석).
     */
    public void update(ContractEdit edit) {
        this.contractor = edit.contractor();
        this.name = edit.name();
        this.status = edit.status();
        this.contractDate = edit.contractDate();
        this.startDate = edit.startDate();
        this.endDate = edit.endDate();
        this.amount = edit.amount();
        this.monthlyAmount = edit.monthlyAmount();
        this.salesRepId = edit.salesRepId();
        this.category = edit.category();
        this.targetInfra = edit.targetInfra();
        this.regularCheck = edit.regularCheck();
        this.note = edit.note();
    }

    /**
     * 낙관적 락 검사 (AC D2-2) — 최신 version을 알려 재조회 후 재시도하게 한다.
     * 계약은 갱신 이력이 자산이라 마지막 쓰기가 조용히 이기면 안 된다.
     */
    public void requireVersion(long expected) {
        if (version != expected) {
            throw new StaleVersionException("최신 계약 version " + version);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getSourceProjectId() {
        return sourceProjectId;
    }

    public String getContractor() {
        return contractor;
    }

    public String getName() {
        return name;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public String getSheetSection() {
        return sheetSection;
    }

    public LocalDate getContractDate() {
        return contractDate;
    }

    public String getContractDateNote() {
        return contractDateNote;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Long getAmount() {
        return amount;
    }

    public Long getMonthlyAmount() {
        return monthlyAmount;
    }

    public Long getSalesRepId() {
        return salesRepId;
    }

    public String getCategory() {
        return category;
    }

    public String getTargetInfra() {
        return targetInfra;
    }

    public String getRegularCheck() {
        return regularCheck;
    }

    public String getNote() {
        return note;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "MaintenanceContract{id=" + id + ", contractor=" + contractor
                + ", name=" + name + ", status=" + status + "}";
    }
}
