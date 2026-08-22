package kr.proten.pms.project.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import kr.proten.pms.audit.AuditAction;
import kr.proten.pms.audit.AuditEntry;
import kr.proten.pms.audit.AuditTrail;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAssignment;
import org.springframework.stereotype.Component;

/**
 * project 모듈의 변경을 감사 로그로 옮긴다 (EPIC G · G2-1).
 *
 * 판정을 여기 모으는 이유가 셋이다.
 * - 무엇을 남길지: 변경 전 스냅샷과 현재 값을 비교해 **바뀐 필드만** 남긴다.
 * - 어떤 action인지: status가 바뀐 변경만 STATE_CHANGE이고 나머지는 UPDATE다
 *   (§5 상태 전이 전용 — PRD-pms v2.1 정리). 호출부가 고르면 그 규칙이 흩어진다.
 * - 프로젝트별 이력에 걸릴지: project 모듈의 변경은 모두 프로젝트 스코프라
 *   projectId를 항상 채운다(G2-1) — 배정처럼 entityId가 프로젝트가 아닌 행도 그렇다.
 */
@Component
class ProjectAuditRecorder {
    private static final String PROJECT = "Project";
    private static final String ASSIGNMENT = "ProjectAssignment";
    private static final String STATUS_FIELD = "status";

    private final AuditTrail auditTrail;

    ProjectAuditRecorder(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    /** 변경 전 값 — 서비스가 엔티티를 바꾸기 **직전에** 떠 둬야 한다. */
    Map<String, Object> snapshot(Project project) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("client", project.getClient());
        state.put("name", project.getName());
        state.put("solution", project.getSolution());
        state.put("engagement", project.getEngagement());
        state.put("managerId", project.getManagerId());
        state.put("contractMm", project.getContractMm());
        state.put("startDate", project.getStartDate());
        state.put("endDate", project.getEndDate());
        state.put(STATUS_FIELD, project.getStatus());
        state.put("progress", project.getProgress());
        // soft 삭제도 필드 변화이므로 스냅샷에 있어야 이력에 남는다 (A4-1)
        state.put("deleted", project.isDeleted());

        return state;
    }

    Map<String, Object> snapshot(ProjectAssignment assignment) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("personId", assignment.getPersonId());
        state.put("role", assignment.getRole());
        state.put("startDate", assignment.getStartDate());
        state.put("endDate", assignment.getEndDate());
        state.put("monthlyMm", assignment.getMonthlyMm());
        state.put(STATUS_FIELD, assignment.getStatus());

        return state;
    }

    /**
     * 생성 이력 (AC A1-1) — 생성은 1건이다.
     * 함께 만들어진 배정은 따로 남기지 않는다: A1-1이 "CREATE 1건"을 못 박았고,
     * 생성 시점의 배정은 프로젝트 생성 요청의 일부다.
     */
    void created(long actorId, Project project) {
        auditTrail.record(new AuditEntry(PROJECT, project.getId(), project.getId(),
                AuditAction.CREATE, actorId, null, snapshot(project)));
    }

    /** 프로젝트 변경 이력 (AC A2-2·A5-1·A7-1·A7-3) — action은 status 변화가 정한다. */
    void changed(long actorId, Project project, Map<String, Object> before) {
        Map<String, Object> after = snapshot(project);
        recordDiff(PROJECT, project.getId(), project.getId(), actionOf(before, after),
                actorId, before, after);
    }

    /** 삭제 이력 (AC A4-1) — 행은 남지만 의도가 삭제이므로 DELETE다. */
    void deleted(long actorId, Project project, Map<String, Object> before) {
        recordDiff(PROJECT, project.getId(), project.getId(), AuditAction.DELETE, actorId,
                before, snapshot(project));
    }

    /** 배정 생성 이력 (AC B1-1). */
    void assignmentCreated(long actorId, ProjectAssignment assignment) {
        auditTrail.record(new AuditEntry(ASSIGNMENT, assignment.getId(),
                assignment.getProjectId(), AuditAction.CREATE, actorId, null,
                snapshot(assignment)));
    }

    /** 배정 수정 이력 (AC B1-4) — 상태 전이 개념이 없으므로 항상 UPDATE다. */
    void assignmentChanged(long actorId, ProjectAssignment assignment,
            Map<String, Object> before) {
        recordDiff(ASSIGNMENT, assignment.getId(), assignment.getProjectId(),
                AuditAction.UPDATE, actorId, before, snapshot(assignment));
    }

    /**
     * 배정 종료 이력 (AC B2-1) — 행은 남지만 의도가 삭제이므로 DELETE다.
     * after를 비우지 않고 종료 상태를 담는다: 언제 어떤 상태로 닫혔는지가 이력의 값이다.
     */
    void assignmentClosed(long actorId, ProjectAssignment assignment,
            Map<String, Object> before) {
        recordDiff(ASSIGNMENT, assignment.getId(), assignment.getProjectId(),
                AuditAction.DELETE, actorId, before, snapshot(assignment));
    }

    private AuditAction actionOf(Map<String, Object> before, Map<String, Object> after) {
        if (Objects.equals(before.get(STATUS_FIELD), after.get(STATUS_FIELD))) {
            return AuditAction.UPDATE;
        }

        return AuditAction.STATE_CHANGE;
    }

    /**
     * 바뀐 필드만 남긴다 — 바뀐 것이 없으면 이력도 없다.
     * G1-1이 요구하는 것은 "변경 1건당 1행"이라 변경이 없는 저장 요청은 대상이 아니다.
     */
    private void recordDiff(
            String entityType,
            Long entityId,
            Long projectId,
            AuditAction action,
            long actorId,
            Map<String, Object> before,
            Map<String, Object> after) {
        Map<String, Object> changedAfter = diff(before, after);

        if (changedAfter.isEmpty()) {
            return;
        }

        auditTrail.record(new AuditEntry(entityType, entityId, projectId, action, actorId,
                diff(after, before), changedAfter));
    }

    /** to에서 from과 값이 달라진 항목만 — 두 방향으로 부르면 before·after 쌍이 된다. */
    private Map<String, Object> diff(Map<String, Object> from, Map<String, Object> to) {
        Map<String, Object> changed = new LinkedHashMap<>();

        to.forEach((field, value) -> {
            if (!Objects.equals(from.get(field), value)) {
                changed.put(field, value);
            }
        });

        return changed;
    }
}
