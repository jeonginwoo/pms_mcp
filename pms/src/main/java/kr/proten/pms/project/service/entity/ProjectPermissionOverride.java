package kr.proten.pms.project.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 프로젝트별 권한 커스텀의 한 칸 (US-A8 · 상위 PRD §4-2).
 *
 * <p><b>기본값과 다른 칸만 행이 된다</b>(AC A8-2): 행이 없다 = §4-2 기본값이다.
 * 기본값을 전부 적재해 두면 §4-2 표가 바뀌는 날 저장된 값이 옛 기본값을 들고 있어
 * 표와 데이터가 조용히 갈린다.
 *
 * <p>{@code @Version}이 없다. 낙관적 락은 {@code Project.version} 하나를 쓴다
 * (AC A8-7 — "`Project.version` 공용") — 매트릭스는 프로젝트의 속성이고, 칸마다
 * 락을 두면 "8칸을 한 번에 저장한다"(A8-2 배치)가 8개의 락을 다루는 일이 된다.
 */
@Entity
@Table(name = "project_permission_overrides")
public class ProjectPermissionOverride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectRole role;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectAction action;
    @Column(nullable = false)
    private boolean allowed;

    protected ProjectPermissionOverride() {
    }

    private ProjectPermissionOverride(
            Long projectId, ProjectRole role, ProjectAction action, boolean allowed) {
        this.projectId = projectId;
        this.role = role;
        this.action = action;
        this.allowed = allowed;
    }

    /**
     * 조정 가능한 칸인지는 {@link ProjectPermissionRules}가 판정하고 호출자가 먼저
     * 거른다 — 여기서 다시 검사하면 같은 규칙이 두 곳에 생긴다(§4-2가 원본).
     */
    public static ProjectPermissionOverride of(
            Long projectId, ProjectRole role, ProjectAction action, boolean allowed) {
        return new ProjectPermissionOverride(projectId, role, action, allowed);
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public ProjectRole getRole() {
        return role;
    }

    public ProjectAction getAction() {
        return action;
    }

    public boolean isAllowed() {
        return allowed;
    }
}
