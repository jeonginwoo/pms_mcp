package kr.proten.pms.identity.internal.infra.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import kr.proten.pms.identity.internal.domain.OrgUnit;

/**
 * OrgUnit 영속 매핑 — 도메인은 순수 record라 JPA 매핑은 infra가 진다 (§0 아키텍처 규칙).
 */
@Entity
@Table(name = "org_units", schema = "identity")
class OrgUnitJpa {
    // 식별자
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // 상위 노드 — 회사(root)만 null. 모듈 내 참조도 id로 (§0: 객체참조 금지)
    private Long parentId;
    // 조직명
    @Column(nullable = false)
    private String name;
    // 낙관적 락
    @Version
    private long version;

    protected OrgUnitJpa() {
    }

    static OrgUnitJpa fromDomain(OrgUnit domain) {
        OrgUnitJpa entity = new OrgUnitJpa();
        entity.id = domain.id();
        entity.parentId = domain.parentId();
        entity.name = domain.name();
        entity.version = domain.version();

        return entity;
    }

    OrgUnit toDomain() {
        return new OrgUnit(id, parentId, name, version);
    }
}
