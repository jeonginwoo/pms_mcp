package kr.proten.pms.person.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import kr.proten.pms.audit.AuditAction;
import kr.proten.pms.audit.AuditEntry;
import kr.proten.pms.audit.AuditTrail;
import kr.proten.pms.person.service.entity.OrgUnit;
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

    private Map<String, Object> state(String field, Object value) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(field, value);

        return state;
    }
}
