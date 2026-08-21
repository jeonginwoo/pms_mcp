package kr.proten.pms.identity.internal.infra.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import kr.proten.pms.identity.internal.domain.PermissionGroup;
import kr.proten.pms.identity.internal.domain.VisibilityScope;

/**
 * PermissionGroup 영속 매핑.
 */
@Entity
@Table(name = "permission_groups", schema = "identity")
class PermissionGroupJpa {
    // 식별자
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // 그룹명
    @Column(nullable = false)
    private String name;
    // 조직 가시성 4단
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VisibilityScope visibilityScope;
    // 기능 플래그 — 프로젝트 생성
    @Column(nullable = false)
    private boolean createProject;
    // 기능 플래그 — 계약 관리
    @Column(nullable = false)
    private boolean manageContracts;
    // 기능 플래그 — 전 프로젝트 관리(PM 간주)
    @Column(nullable = false)
    private boolean manageAllProjects;
    // 기능 플래그 — 사용자/조직/권한 관리
    @Column(nullable = false)
    private boolean manageOrg;
    // 시스템 고정 그룹(관리자) — 수정·삭제 불가
    @Column(nullable = false)
    private boolean systemFixed;
    // 낙관적 락
    @Version
    private long version;

    protected PermissionGroupJpa() {
    }

    static PermissionGroupJpa fromDomain(PermissionGroup domain) {
        PermissionGroupJpa entity = new PermissionGroupJpa();
        entity.id = domain.id();
        entity.name = domain.name();
        entity.visibilityScope = domain.visibilityScope();
        entity.createProject = domain.createProject();
        entity.manageContracts = domain.manageContracts();
        entity.manageAllProjects = domain.manageAllProjects();
        entity.manageOrg = domain.manageOrg();
        entity.systemFixed = domain.systemFixed();
        entity.version = domain.version();

        return entity;
    }

    PermissionGroup toDomain() {
        return new PermissionGroup(
                id,
                name,
                visibilityScope,
                createProject,
                manageContracts,
                manageAllProjects,
                manageOrg,
                systemFixed,
                version);
    }
}
