package kr.proten.pms.common.audit.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import kr.proten.pms.common.audit.service.AuditAction;
import kr.proten.pms.common.audit.service.AuditSource;

/**
 * 감사 로그 한 행 (PRD-pms §4 · EPIC G).
 *
 * append-only가 이 엔티티의 불변식이다: 상태를 바꾸는 메서드가 없고 모든 컬럼이
 * updatable=false 라, 실수로 수정 경로가 생겨도 JPA가 UPDATE를 내지 않는다(G1-2).
 * @Version이 없는 이유도 같다 — 동시 수정될 수 있는 행이 아니다.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;
    @Column(name = "entity_id", nullable = false, updatable = false)
    private Long entityId;
    // 프로젝트 스코프 이벤트만 채운다 — 프로젝트별 이력의 필터 컬럼 (G2-1)
    @Column(name = "project_id", updatable = false)
    private Long projectId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditAction action;
    @Column(name = "actor_id", nullable = false, updatable = false)
    private Long actorId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditSource source;
    // 바뀐 필드만 담은 JSON 스냅샷 — 표현은 기록 계층이 정한다
    @Column(name = "before_state", updatable = false)
    private String beforeState;
    @Column(name = "after_state", updatable = false)
    private String afterState;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    private AuditLog(
            String entityType,
            Long entityId,
            Long projectId,
            AuditAction action,
            long actorId,
            AuditSource source,
            String beforeState,
            String afterState) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.projectId = projectId;
        this.action = action;
        this.actorId = actorId;
        this.source = source;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.createdAt = Instant.now();
    }

    /**
     * 기록 시각은 인자로 받지 않는다 — 감사 로그의 시각은 호출자가 고를 값이 아니다
     * (행위자·시각이 재개 사유를 대신하는 근거 — A7-3).
     */
    public static AuditLog of(
            String entityType,
            Long entityId,
            Long projectId,
            AuditAction action,
            long actorId,
            AuditSource source,
            String beforeState,
            String afterState) {
        return new AuditLog(entityType, entityId, projectId, action, actorId, source,
                beforeState, afterState);
    }

    public Long getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public AuditAction getAction() {
        return action;
    }

    public Long getActorId() {
        return actorId;
    }

    public AuditSource getSource() {
        return source;
    }

    public String getBeforeState() {
        return beforeState;
    }

    public String getAfterState() {
        return afterState;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** before·after는 담지 않는다 — 로그로 새면 원문·PII 금지(conventions §6)를 어긴다. */
    @Override
    public String toString() {
        return "AuditLog{id=" + id + ", entityType=" + entityType + ", entityId=" + entityId
                + ", action=" + action + ", actorId=" + actorId + ", source=" + source + "}";
    }
}
