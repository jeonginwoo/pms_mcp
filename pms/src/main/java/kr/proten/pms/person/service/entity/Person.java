package kr.proten.pms.person.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import kr.proten.pms.common.exception.StaleVersionException;

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

    /**
     * 이름만 바꾼다 (AC H1-2 내 프로필).
     *
     * <p><b>{@link #update}를 쓰지 않는 이유</b>: 그쪽은 소속·직급·권한 그룹까지 받는
     * 관리자 경로(E2-2)라, 내 프로필 수정이 그것을 부르면 <b>자기 권한 그룹을 스스로
     * 바꿀 수 있는 길</b>이 열린다. 지금 값을 그대로 다시 넘기는 방식도 안전하지 않다 —
     * 나중에 칸이 하나 늘면 프로필 수정이 그것을 조용히 초기화한다.
     */
    public void rename(String name) {
        this.name = name;
    }

    /**
     * 이름·소속·직급·권한 그룹·집계 대상 여부를 바꾼다
     * (AC E2-2 — 그룹 부여도 이 경로다, 2026-08-09 ⑦ · {@code billable}은 2026-08-26 추가).
     *
     * <p><b>{@code billable}이 늘어난 경위</b>: 구 주석은 "부록 B가 조직 단위로 정한다"를
     * 근거로 이 필드를 뺐는데, 그것은 부록 B의 <b>시드 적재 기준</b>을 읽은 것이고 같은
     * 문단이 <b>"플래그는 운영 중 개인 단위 수정 가능"</b>으로 끝난다. 그래서 쓰기 경로가
     * 하나도 없는 상태였다(§12 등재 — 2026-08-24). 적재는 조직 단위로 하되 운영 중
     * 개인 예외는 이 경로가 든다.
     *
     * <p>{@code capacity}는 여전히 인자에 없다 — 그쪽은 월별 {@code Capacity}가 우선하는
     * 가동률 <b>분모</b>라 사람 정보 수정과 판단이 다르다(C1 산식).
     *
     * <p>판정은 E2-2의 "사용자/조직/권한 관리" 플래그 그대로다. 새 관문을 두지 않는 것이
     * 맞다: 집계 모집단을 정하는 일은 조직 운영이고, 그 플래그가 이미 그 권한이다.
     */
    public void update(
            String name, Long orgUnitId, Long gradeId, Long groupId, boolean billable) {
        this.name = name;
        this.orgUnitId = orgUnitId;
        this.gradeId = gradeId;
        this.groupId = groupId;
        this.billable = billable;
    }

    /**
     * 소속만 옮긴다 (AC E1-1) — 가시성은 저장된 값이 아니라 이 필드에서 파생되므로
     * 다음 요청부터 바로 새 범위가 적용된다.
     *
     * <p>인력 수정({@link #update})과 입구를 나눈 것은 §7이 라우트를 나눴기 때문이다:
     * 이동은 조직 개편에서 이름·직급을 건드리지 않고 일어난다.
     */
    public void moveTo(Long orgUnitId) {
        this.orgUnitId = orgUnitId;
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

    /**
     * 낙관적 락 검사 (AC E2-2) — 최신 version을 알려 재조회 후 재시도하게 한다.
     * 관리 화면은 여러 관리자가 같은 행을 열어 두는 자리라 마지막 쓰기가 조용히
     * 이기면 앞사람의 변경이 흔적 없이 사라진다 (§7 동시성 규약).
     */
    public void requireVersion(long expected) {
        if (version != expected) {
            throw new StaleVersionException("최신 인원 version " + version);
        }
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Person{id=" + id + ", name=" + name + ", orgUnitId=" + orgUnitId + "}";
    }
}
