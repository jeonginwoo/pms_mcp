package kr.proten.pms.person.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import kr.proten.pms.common.exception.StaleVersionException;

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

    /**
     * 그룹 정의를 바꾼다 (AC E5-2) — 판정·가시성·404 은닉이 다음 요청부터 새 정의를 탄다.
     *
     * <p>{@code systemFixed}는 인자에 없다: 고정 여부는 그룹이 만들어질 때 정해지고
     * 수정으로 뒤집을 수 있는 값이 아니다(E5-3 — 그럴 수 있으면 관리자 그룹을 풀어
     * 스스로를 잠글 수 있다). 애초에 고정 그룹은 이 메서드에 닿지 않는다.
     */
    public void update(
            String name,
            VisibilityScope visibilityScope,
            boolean createProject,
            boolean manageContracts,
            boolean manageAllProjects,
            boolean manageOrg) {
        this.name = name;
        this.visibilityScope = visibilityScope;
        this.createProject = createProject;
        this.manageContracts = manageContracts;
        this.manageAllProjects = manageAllProjects;
        this.manageOrg = manageOrg;
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

    /**
     * 낙관적 락 검사 (AC E5-2) — 최신 version을 알려 재조회 후 재시도하게 한다.
     * 관리 화면은 여러 관리자가 같은 행을 열어 두는 자리라 마지막 쓰기가 조용히
     * 이기면 앞사람의 변경이 흔적 없이 사라진다 (§7 동시성 규약).
     */
    public void requireVersion(long expected) {
        if (version != expected) {
            throw new StaleVersionException("최신 그룹 version " + version);
        }
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
