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

/**
 * 유지보수 사이트 — 계약이 커버하는 고객사 한 곳 (계약:사이트 1:N).
 *
 * <p><b>담당 엔지니어의 정본이 여기다</b>(PRD-pms §4): 이슈를 등록하면 기본 배정이
 * 이 값에서 나온다(D3-1). {@code engineerId}가 비어 있는 것은 결함이 아니라 상태다 —
 * 신규 예정·종료 섹션의 사이트는 미배정이고, "미배정 이슈" 필터(D3-4)가 그것을
 * 화면에서 드러낸다.
 *
 * <p>{@code serverSpec}이 사이트에 있는 이유(2026-08-23 결정): 시드는 계약 행에
 * 적어 두었지만 그 값이 45개 사이트 중 한 곳을 가리킨다("태광그룹- 1번서버 …").
 * 서버는 사이트마다 다르다.
 */
@Entity
@Table(name = "maintenance_sites")
public class MaintenanceSite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "contract_id", nullable = false)
    private Long contractId;
    @Column(nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    private SiteChannel channel;
    @Column(name = "server_spec")
    private String serverSpec;
    // 담당 엔지니어 — 미배정(null)은 정상 상태다
    @Column(name = "engineer_id")
    private Long engineerId;
    @Version
    private long version;

    protected MaintenanceSite() {
    }

    private MaintenanceSite(
            Long contractId, String name, SiteChannel channel, String serverSpec, Long engineerId) {
        this.contractId = contractId;
        this.name = name;
        this.channel = channel;
        this.serverSpec = serverSpec;
        this.engineerId = engineerId;
    }

    public static MaintenanceSite of(
            Long contractId, String name, SiteChannel channel, String serverSpec, Long engineerId) {
        return new MaintenanceSite(contractId, name, channel, serverSpec, engineerId);
    }

    public Long getId() {
        return id;
    }

    public Long getContractId() {
        return contractId;
    }

    public String getName() {
        return name;
    }

    public SiteChannel getChannel() {
        return channel;
    }

    public String getServerSpec() {
        return serverSpec;
    }

    public Long getEngineerId() {
        return engineerId;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "MaintenanceSite{id=" + id + ", contractId=" + contractId + ", name=" + name + "}";
    }
}
