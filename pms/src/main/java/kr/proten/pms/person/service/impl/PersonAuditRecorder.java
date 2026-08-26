package kr.proten.pms.person.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import kr.proten.pms.audit.AuditAction;
import kr.proten.pms.audit.AuditEntry;
import kr.proten.pms.audit.AuditTrail;
import kr.proten.pms.person.service.entity.Grade;
import kr.proten.pms.person.service.entity.OrgUnit;
import kr.proten.pms.person.service.entity.PermissionGroup;
import kr.proten.pms.person.AccountContact;
import kr.proten.pms.person.service.entity.Person;
import org.springframework.stereotype.Component;

/**
 * person 모듈의 변경을 감사 로그로 옮긴다 (EPIC G · E1-1·E2-1).
 *
 * projectId는 채우지 않는다 — 조직·계정 변경은 프로젝트 스코프가 아니므로 프로젝트별
 * 이력(G2-2)에 걸리지 않고 통합 로그(G1-3)에만 나타난다(§4 projectId 정의).
 */
@Component
class PersonAuditRecorder {
    private static final String PERSON = "Person";
    private static final String ORG_UNIT = "OrgUnit";
    private static final String GRADE = "Grade";
    private static final String PERMISSION_GROUP = "PermissionGroup";

    private final AuditTrail auditTrail;

    PersonAuditRecorder(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    /** 인원 등록 (AC E2-1) — 계정 생성은 같은 행위의 일부라 따로 남기지 않는다. */
    void personCreated(long actorId, Person person) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("name", person.getName());
        after.put("orgUnitId", person.getOrgUnitId());
        after.put("gradeId", person.getGradeId());
        after.put("groupId", person.getGroupId());
        after.put("active", person.isActive());

        auditTrail.record(new AuditEntry(PERSON, person.getId(), null, AuditAction.CREATE,
                actorId, null, after));
    }

    /** 조직 노드 신설 (AC E3-1). */
    void orgUnitCreated(long actorId, OrgUnit orgUnit) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("name", orgUnit.getName());
        after.put("parentId", orgUnit.getParentId());

        auditTrail.record(new AuditEntry(ORG_UNIT, orgUnit.getId(), null, AuditAction.CREATE,
                actorId, null, after));
    }

    /** 인원 비활성 (AC E2-3) — 행은 남으므로 상태 변화를 담는다. */
    void personDeactivated(long actorId, Person person) {
        auditTrail.record(new AuditEntry(PERSON, person.getId(), null, AuditAction.DELETE,
                actorId, state("active", true), state("active", false)));
    }

    /** 조직 노드 삭제 (AC E3-3) — 실제로 행이 사라지므로 after는 없다. */
    void orgUnitDeleted(long actorId, OrgUnit orgUnit) {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("name", orgUnit.getName());
        before.put("parentId", orgUnit.getParentId());

        auditTrail.record(new AuditEntry(ORG_UNIT, orgUnit.getId(), null, AuditAction.DELETE,
                actorId, before, null));
    }

    /** 변경 전 스냅샷 — 서비스가 엔티티를 바꾸기 **직전에** 떠 둬야 한다 (E2-2). */
    Map<String, Object> snapshot(Person person) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", person.getName());
        state.put("orgUnitId", person.getOrgUnitId());
        state.put("gradeId", person.getGradeId());
        state.put("groupId", person.getGroupId());
        // 2026-08-26: billable이 E2-2로 수정 가능해졌다. 여기 없으면 그 필드만 바꾼
        // 요청은 diff가 비어 **감사 행이 0건**이 된다(H1-2의 email이 같은 자리였다) —
        // 가동률 집계 모집단을 바꾸는 일이라 흔적 없이 일어나면 안 된다
        state.put("billable", person.isBillable());

        return state;
    }

    Map<String, Object> snapshot(Grade grade) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", grade.getName());
        state.put("coeff", grade.getCoeff());

        return state;
    }

    Map<String, Object> snapshot(PermissionGroup group) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", group.getName());
        state.put("visibilityScope", group.getVisibilityScope());
        state.put("createProject", group.isCreateProject());
        state.put("manageContracts", group.isManageContracts());
        state.put("manageAllProjects", group.isManageAllProjects());
        state.put("manageOrg", group.isManageOrg());

        return state;
    }

    /** 인원 수정·소속 이동 (AC E2-2·E1-1) — 소속 이동도 §5 상태 전이가 아니라 UPDATE다(v2.1). */
    void personChanged(long actorId, Person person, Map<String, Object> before) {
        recordDiff(PERSON, person.getId(), actorId, before, snapshot(person));
    }

    /**
     * 연락처 변경 (AC H1-2) — email·phone은 auth의 {@code users} 행이라
     * {@link #snapshot(Person)}에 들어오지 않는다.
     *
     * <p><b>이것이 없으면 로그인 ID 변경이 흔적 없이 일어난다</b>(2026-08-25 리뷰가
     * 잡았다): 이름을 그대로 두고 email만 바꾸면 person 스냅샷의 diff가 비어 감사
     * 행이 <b>0건</b>이었다. AC H1-2가 요구한 "AuditLog UPDATE"가 성립하지 않았다.
     *
     * <p>entityType이 {@code Person}인 이유: 감사의 주어는 <b>사람</b>이고, 계정은
     * 그 사람의 속성이다. 별 entityType을 두면 "이 사람에게 무슨 일이 있었나"를
     * 두 번 조회해야 한다.
     */
    void contactChanged(long actorId, Person person, AccountContact before, AccountContact after) {
        Map<String, Object> from = contactState(before);
        Map<String, Object> to = contactState(after);

        recordDiff(PERSON, person.getId(), actorId, from, to);
    }

    private static Map<String, Object> contactState(AccountContact contact) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("email", contact.email());
        state.put("phone", contact.phone());

        return state;
    }

    /** 조직 개명 (AC E3-2). */
    void orgUnitRenamed(long actorId, OrgUnit orgUnit, String beforeName) {
        recordDiff(ORG_UNIT, orgUnit.getId(), actorId,
                state("name", beforeName), state("name", orgUnit.getName()));
    }

    /** 조직 이동 (AC E3-5) — 개명과 같은 UPDATE다. 바뀐 칸이 parentId 하나뿐이다. */
    void orgUnitMoved(long actorId, OrgUnit orgUnit, Long beforeParentId) {
        recordDiff(ORG_UNIT, orgUnit.getId(), actorId,
                state("parentId", beforeParentId), state("parentId", orgUnit.getParentId()));
    }

    /** 직급 등록·수정·삭제 (AC E4-1·E4-2·E4-3) — 삭제는 행이 사라지므로 after가 없다. */
    void gradeCreated(long actorId, Grade grade) {
        auditTrail.record(new AuditEntry(GRADE, grade.getId(), null, AuditAction.CREATE,
                actorId, null, snapshot(grade)));
    }

    void gradeChanged(long actorId, Grade grade, Map<String, Object> before) {
        recordDiff(GRADE, grade.getId(), actorId, before, snapshot(grade));
    }

    void gradeDeleted(long actorId, Grade grade) {
        auditTrail.record(new AuditEntry(GRADE, grade.getId(), null, AuditAction.DELETE,
                actorId, snapshot(grade), null));
    }

    /** 권한 그룹 등록·수정·삭제 (AC E5-1·E5-2·E5-3·E5-4). */
    void permissionGroupCreated(long actorId, PermissionGroup group) {
        auditTrail.record(new AuditEntry(PERMISSION_GROUP, group.getId(), null,
                AuditAction.CREATE, actorId, null, snapshot(group)));
    }

    void permissionGroupChanged(long actorId, PermissionGroup group, Map<String, Object> before) {
        recordDiff(PERMISSION_GROUP, group.getId(), actorId, before, snapshot(group));
    }

    void permissionGroupDeleted(long actorId, PermissionGroup group) {
        auditTrail.record(new AuditEntry(PERMISSION_GROUP, group.getId(), null,
                AuditAction.DELETE, actorId, snapshot(group), null));
    }

    /**
     * 바뀐 필드만 남긴다 — 바뀐 것이 없으면 행을 만들지 않는다.
     *
     * <p>{@code ProjectAuditRecorder.recordDiff}와 같은 규칙이다. 두 모듈이 각자 갖는
     * 이유는 스냅샷 대상이 다르기 때문이고(감사 모듈은 도메인을 알지 못한다),
     * 판정 자체는 같아야 하므로 여기서도 "변경 없음 = 행 없음"을 지킨다.
     *
     * <p>{@code projectId}는 언제나 null이다: person 모듈의 변경은 조직·계정 변경이라
     * 프로젝트 스코프가 아니고(G2-1), 그래서 통합 로그(G1-3)에만 나온다.
     */
    private void recordDiff(
            String entityType,
            Long entityId,
            long actorId,
            Map<String, Object> before,
            Map<String, Object> after) {
        Map<String, Object> changedBefore = new LinkedHashMap<>();
        Map<String, Object> changedAfter = new LinkedHashMap<>();

        after.forEach((field, value) -> {
            if (!Objects.equals(before.get(field), value)) {
                changedBefore.put(field, before.get(field));
                changedAfter.put(field, value);
            }
        });

        if (changedAfter.isEmpty()) {
            return;
        }

        auditTrail.record(new AuditEntry(entityType, entityId, null, AuditAction.UPDATE,
                actorId, changedBefore, changedAfter));
    }

    private Map<String, Object> state(String field, Object value) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(field, value);

        return state;
    }
}
