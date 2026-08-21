package kr.proten.pms.person.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 사람 — 조직·직급·권한 그룹 소속과 가동률 속성 (PRD-pms §4).
 * 모듈 간 연결은 id로만 하므로(§0) 다른 모듈의 엔티티를 참조하는 필드는 없다.
 */
@Entity
@Table(name = "people")
public class Person {
    @Id
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(name = "org_unit_id", nullable = false)
    private Long orgUnitId;
    @Column(name = "grade_id", nullable = false)
    private Long gradeId;
    // 권한 그룹 — 가시성 scope와 기능 플래그의 출처
    @Column(name = "group_id", nullable = false)
    private Long groupId;
    // 월 가용 M/M 기본값 (1.0 = 풀타임)
    @Column(nullable = false)
    private double capacity;
    // 가동률 집계 모집단 여부 (상위 PRD §3)
    @Column(nullable = false)
    private boolean billable;
    // 시스템 계정 플래그 — 삭제·수정 불가, 인력·가동률·배정 목록 제외
    @Column(name = "system_account", nullable = false)
    private boolean system;
    // soft 삭제 상태 — false면 목록·단건에서 제외(과거 데이터는 보존)
    @Column(nullable = false)
    private boolean active;
    @Version
    private long version;

    protected Person() {
    }

    private Person(
            Long id,
            String name,
            Long orgUnitId,
            Long gradeId,
            Long groupId,
            double capacity,
            boolean billable,
            boolean system,
            boolean active) {
        this.id = id;
        this.name = name;
        this.orgUnitId = orgUnitId;
        this.gradeId = gradeId;
        this.groupId = groupId;
        this.capacity = capacity;
        this.billable = billable;
        this.system = system;
        this.active = active;
    }

    /** 인원을 만든다 — 식별자를 받는 이유는 OrgUnit.of와 같다(시드 정본 보존). */
    public static Person of(
            Long id,
            String name,
            Long orgUnitId,
            Long gradeId,
            Long groupId,
            double capacity,
            boolean billable,
            boolean system,
            boolean active) {
        return new Person(id, name, orgUnitId, gradeId, groupId, capacity, billable, system,
                active);
    }

    /**
     * soft 비활성 (AC E2-3) — 로그인 차단·목록 제외.
     * 행을 지우지 않는 이유: 과거 배정·감사 로그·집계가 이 인원을 가리키고 있고,
     * 그것들은 그때의 사실이라 사라져서는 안 된다.
     */
    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public Long getGradeId() {
        return gradeId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public double getCapacity() {
        return capacity;
    }

    public boolean isBillable() {
        return billable;
    }

    public boolean isSystem() {
        return system;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Person{id=" + id + ", name=" + name + ", orgUnitId=" + orgUnitId + "}";
    }
}
