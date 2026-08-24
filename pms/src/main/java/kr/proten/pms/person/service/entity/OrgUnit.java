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

    /**
     * 노드 이름을 바꾼다 (AC E3-2).
     *
     * <p>소속 인원·프로젝트는 {@code orgUnitId}로 참조하므로 표시가 즉시 동기화된다 —
     * 비정규화된 이름 컬럼을 두지 않는 이유가 이것이다(E3-2 본문).
     */
    public void rename(String name) {
        this.name = name;
    }

    /**
     * 상위 노드를 바꾼다 (AC E3-5).
     *
     * <p>순환 검사는 <b>여기서 하지 않는다</b>: 자기 subtree 안인지 판정하려면 트리 전체를
     * 알아야 하는데 엔티티는 자기 행만 안다. 그 판정은 노드를 모두 들고 있는 서비스가 한다
     * (같은 이유로 "부모가 실재하는가"도 서비스 몫이다 — E3-1 생성과 같은 판정).
     */
    public void moveTo(Long parentId) {
        this.parentId = parentId;
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
