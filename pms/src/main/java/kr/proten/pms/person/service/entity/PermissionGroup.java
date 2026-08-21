package kr.proten.pms.person.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 권한 그룹 — 구 orgRole 4단의 일반화 (규칙 원본은 상위 PRD §4-3).
 * 가시성 scope 4단 + 프로젝트 밖 기능 플래그 4종. 판정·가시성·404 은닉이 전부
 * 그룹 정의를 따른다. systemFixed 그룹(관리자)은 수정·삭제 불가 — 자기 잠금 방지.
 */
@Entity
@Table(name = "permission_groups")
public class PermissionGroup {
    @Id
    private Long id;
    @Column(nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility_scope", nullable = false)
    private VisibilityScope visibilityScope;
    @Column(name = "create_project", nullable = false)
    private boolean createProject;
    @Column(name = "manage_contracts", nullable = false)
    private boolean manageContracts;
    @Column(name = "manage_all_projects", nullable = false)
    private boolean manageAllProjects;
    @Column(name = "manage_org", nullable = false)
    private boolean manageOrg;
    // 시스템 고정 그룹(관리자) — 수정·삭제 불가
    @Column(name = "system_fixed", nullable = false)
    private boolean systemFixed;
    @Version
    private long version;

    protected PermissionGroup() {
    }

    private PermissionGroup(
            Long id,
            String name,
            VisibilityScope visibilityScope,
            boolean createProject,
            boolean manageContracts,
            boolean manageAllProjects,
            boolean manageOrg,
            boolean systemFixed) {
        this.id = id;
        this.name = name;
        this.visibilityScope = visibilityScope;
        this.createProject = createProject;
        this.manageContracts = manageContracts;
        this.manageAllProjects = manageAllProjects;
        this.manageOrg = manageOrg;
        this.systemFixed = systemFixed;
    }

    /** 권한 그룹을 만든다 — 식별자를 받는 이유는 OrgUnit.of와 같다(시드 정본 보존). */
    public static PermissionGroup of(
            Long id,
            String name,
            VisibilityScope visibilityScope,
            boolean createProject,
            boolean manageContracts,
            boolean manageAllProjects,
            boolean manageOrg,
            boolean systemFixed) {
        return new PermissionGroup(id, name, visibilityScope, createProject, manageContracts,
                manageAllProjects, manageOrg, systemFixed);
    }

    public boolean isCreateProject() {
        return createProject;
    }

    public boolean isManageContracts() {
        return manageContracts;
    }

    public boolean isManageAllProjects() {
        return manageAllProjects;
    }

    public boolean isManageOrg() {
        return manageOrg;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public VisibilityScope getVisibilityScope() {
        return visibilityScope;
    }

    public boolean isSystemFixed() {
        return systemFixed;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "PermissionGroup{id=" + id + ", name=" + name
                + ", visibilityScope=" + visibilityScope + "}";
    }
}
