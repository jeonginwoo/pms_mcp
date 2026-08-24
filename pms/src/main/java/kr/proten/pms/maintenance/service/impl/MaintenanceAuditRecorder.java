package kr.proten.pms.maintenance.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import kr.proten.pms.audit.AuditAction;
import kr.proten.pms.audit.AuditEntry;
import kr.proten.pms.audit.AuditTrail;
import kr.proten.pms.maintenance.service.entity.MaintenanceContract;
import kr.proten.pms.maintenance.service.entity.MaintenanceIssue;
import kr.proten.pms.maintenance.service.entity.MaintenanceSite;
import org.springframework.stereotype.Component;

/**
 * maintenance 모듈의 변경을 감사 로그로 옮긴다 (EPIC G · D2-1·D2-2·D2-4 · D3-1·D3-2).
 *
 * projectId는 채우지 않는다 — 계약은 프로젝트 스코프가 아니다(§4 projectId 정의).
 * 이관으로 생긴 계약은 {@code sourceProjectId}로 프로젝트와 이어져 있지만 그것을
 * 감사의 projectId로 쓰면 프로젝트별 이력(G2-2)에 유지보수 계약 변경이 섞여 나온다.
 * 그 판단은 이관(D1)이 실제로 생길 때 할 일이고, 지금 시드 105건은 전부
 * sourceProjectId가 null이라 실효 차이도 없다 — 그래서 통합 로그(G1-3)에만 나온다.
 *
 * 상태 변경도 UPDATE다: {@code STATE_CHANGE}는 §5 프로젝트 상태 전이 전용이고
 * (AuditAction 주석) 계약 상태는 그 상태 기계가 아니다.
 *
 * <b>관찰(2026-08-24)</b>: "바뀐 필드만 남긴다" diff가 이로써 세 모듈에 같은 모양으로
 * 있다(project·person·maintenance). 도메인을 모르는 Map 연산이라 audit 모듈로
 * 올릴 수 있지만 그것은 모듈 루트를 넓히는 일이라 이번 작업 범위 밖이다 —
 * {@code PersonAuditRecorder}가 모듈별로 갖는 이유를 명시해 둔 그 선례를 따른다.
 */
@Component
class MaintenanceAuditRecorder {
    private static final String CONTRACT = "MaintenanceContract";
    private static final String SITE = "MaintenanceSite";
    private static final String ISSUE = "MaintenanceIssue";

    private final AuditTrail auditTrail;

    MaintenanceAuditRecorder(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    /** 계약 직접 등록 (AC D2-1). */
    void contractCreated(long actorId, MaintenanceContract contract) {
        auditTrail.record(new AuditEntry(CONTRACT, contract.getId(), null, AuditAction.CREATE,
                actorId, null, snapshot(contract)));
    }

    /** 계약 수정 (AC D2-2) — 상태 {종료}로 가는 것도 여기로 남는다(삭제 API가 없다). */
    void contractChanged(long actorId, MaintenanceContract contract, Map<String, Object> before) {
        recordDiff(CONTRACT, contract.getId(), actorId, before, snapshot(contract));
    }

    /** 사이트 등록 (AC D2-4) — 연락처는 사이트의 일부라 따로 남기지 않는다. */
    void siteCreated(long actorId, MaintenanceSite site) {
        auditTrail.record(new AuditEntry(SITE, site.getId(), null, AuditAction.CREATE,
                actorId, null, snapshot(site)));
    }

    /** 사이트 수정 (AC D2-4). */
    void siteChanged(long actorId, MaintenanceSite site, Map<String, Object> before) {
        recordDiff(SITE, site.getId(), actorId, before, snapshot(site));
    }

    /** 이슈 등록 (AC D3-1). */
    void issueCreated(long actorId, MaintenanceIssue issue) {
        auditTrail.record(new AuditEntry(ISSUE, issue.getId(), null, AuditAction.CREATE,
                actorId, null, snapshot(issue)));
    }

    /**
     * 이슈 처리 (AC D3-2) — 상태 전이도 UPDATE다.
     *
     * <p>{@code STATE_CHANGE}가 아닌 것은 계약 상태와 같은 이유다: 그 액션은 §5
     * 프로젝트 상태 전이 전용이고({@code AuditAction} 주석) 이슈 상태는 그 상태 기계가
     * 아니다. 코멘트(D3-3)는 여기 오지 않는다 — 그쪽은 자기가 이미 불변 기록이다
     * ({@code IssueCommandServiceImpl#addComment} 주석).
     */
    void issueChanged(long actorId, MaintenanceIssue issue, Map<String, Object> before) {
        recordDiff(ISSUE, issue.getId(), actorId, before, snapshot(issue));
    }

    /** 변경 전 스냅샷 — 서비스가 엔티티를 바꾸기 <b>직전에</b> 떠 둬야 한다. */
    Map<String, Object> snapshot(MaintenanceContract contract) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("contractor", contract.getContractor());
        state.put("name", contract.getName());
        state.put("status", contract.getStatus());
        state.put("contractDate", contract.getContractDate());
        state.put("startDate", contract.getStartDate());
        state.put("endDate", contract.getEndDate());
        state.put("amount", contract.getAmount());
        state.put("monthlyAmount", contract.getMonthlyAmount());
        state.put("salesRepId", contract.getSalesRepId());
        state.put("category", contract.getCategory());
        state.put("targetInfra", contract.getTargetInfra());
        state.put("regularCheck", contract.getRegularCheck());
        state.put("note", contract.getNote());

        return state;
    }

    Map<String, Object> snapshot(MaintenanceSite site) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", site.getName());
        state.put("channel", site.getChannel());
        state.put("serverSpec", site.getServerSpec());
        state.put("engineerId", site.getEngineerId());

        return state;
    }

    Map<String, Object> snapshot(MaintenanceIssue issue) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("siteId", issue.getSiteId());
        state.put("type", issue.getType());
        state.put("title", issue.getTitle());
        state.put("status", issue.getStatus());
        state.put("assigneeId", issue.getAssigneeId());
        state.put("completedAt", issue.getCompletedAt());

        return state;
    }

    /**
     * 바뀐 필드만 남긴다 — 바뀐 것이 없으면 행을 만들지 않는다
     * ({@code ProjectAuditRecorder}·{@code PersonAuditRecorder}와 같은 규칙).
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
}
