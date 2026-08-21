package kr.proten.pms.person.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 조직 노드 — 회사(root) → 부문 → 팀 → 임의 깊이 트리 (PRD-pms §4).
 * "부문"·"팀"은 트리 상 위치의 파생 개념이라 별도 타입을 두지 않는다.
 */
@Entity
@Table(name = "org_units")
public class OrgUnit {
    @Id
    private Long id;
    // 상위 노드 id — 회사(root)만 null
    @Column(name = "parent_id")
    private Long parentId;
    @Column(nullable = false)
    private String name;
    @Version
    private long version;

    protected OrgUnit() {
    }

    private OrgUnit(Long id, Long parentId, String name) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
    }

    /**
     * 조직 노드를 만든다.
     * 식별자를 받는 이유: 조직·직급·권한 그룹은 시드가 정본인 참조 데이터라
     * 적재 시 시드 id를 그대로 보존해야 한다 (시드 적재기 자체는 이번 범위 밖).
     */
    public static OrgUnit of(Long id, Long parentId, String name) {
        return new OrgUnit(id, parentId, name);
    }

    /** 회사(root) 노드 여부. */
    public boolean isRoot() {
        return parentId == null;
    }

    public Long getId() {
        return id;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "OrgUnit{id=" + id + ", parentId=" + parentId + ", name=" + name + "}";
    }
}
