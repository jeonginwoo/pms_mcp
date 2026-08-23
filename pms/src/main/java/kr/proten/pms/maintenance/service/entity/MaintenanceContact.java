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
 * 사이트 담당자 연락처 — 구 시트 담당자 정보 텍스트 블롭의 정규화 (PRD-pms §4).
 *
 * <p><b>원문({@code raw})을 함께 보존한다</b>(2026-08-23 결정): 시트의 형식이
 * 불규칙해서 이름·직급·전화·이메일이 다 있는 것, 전화만 있는 것, 회사명이 앞에
 * 붙은 것이 섞여 있다. 완전 파싱은 실패가 남으므로 확실히 뽑히는 전화·이메일만
 * 컬럼에 담고 나머지는 원문이 답한다 — 파싱 실패가 정보 유실로 이어지지 않게
 * 하는 것이 목적이다.
 */
@Entity
@Table(name = "maintenance_contacts")
public class MaintenanceContact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "site_id", nullable = false)
    private Long siteId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContactParty party;
    private String name;
    private String title;
    private String phone;
    private String email;
    // 시트 원문 — 파싱이 놓친 것의 유일한 출처다
    @Column(nullable = false)
    private String raw;
    @Version
    private long version;

    protected MaintenanceContact() {
    }

    private MaintenanceContact(
            Long siteId, ContactParty party, ContactDetails details, String raw) {
        this.siteId = siteId;
        this.party = party;
        this.name = details.name();
        this.title = details.title();
        this.phone = details.phone();
        this.email = details.email();
        this.raw = raw;
    }

    public static MaintenanceContact of(
            Long siteId, ContactParty party, ContactDetails details, String raw) {
        return new MaintenanceContact(siteId, party, details, raw);
    }

    public Long getId() {
        return id;
    }

    public Long getSiteId() {
        return siteId;
    }

    public ContactParty getParty() {
        return party;
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getRaw() {
        return raw;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "MaintenanceContact{id=" + id + ", siteId=" + siteId + ", party=" + party + "}";
    }
}
