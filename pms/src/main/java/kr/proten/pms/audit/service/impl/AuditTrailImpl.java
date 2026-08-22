package kr.proten.pms.audit.service.impl;

import java.util.Map;
import kr.proten.pms.audit.AuditEntry;
import kr.proten.pms.audit.AuditTrail;
import kr.proten.pms.audit.repository.AuditLogRepository;
import kr.proten.pms.audit.service.entity.AuditLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 감사 기록 (EPIC G) — 스냅샷을 JSON으로 굳혀 한 행으로 남긴다.
 *
 * 트랜잭션을 새로 열지 않는다(기본 REQUIRED): 변경과 이력이 같은 커밋에 묶여야
 * "일어난 변경만 남는다"가 성립한다.
 */
@Service
@Transactional
class AuditTrailImpl implements AuditTrail {
    private final AuditLogRepository auditLogRepository;
    private final AuditSourceResolver auditSourceResolver;
    // 부트가 구성한 매퍼를 쓴다 — 직접 만들면 등록된 모듈·설정이 조용히 빠진다
    private final JsonMapper jsonMapper;

    AuditTrailImpl(
            AuditLogRepository auditLogRepository,
            AuditSourceResolver auditSourceResolver,
            JsonMapper jsonMapper) {
        this.auditLogRepository = auditLogRepository;
        this.auditSourceResolver = auditSourceResolver;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void record(AuditEntry entry) {
        auditLogRepository.save(AuditLog.of(
                entry.entityType(),
                entry.entityId(),
                entry.projectId(),
                entry.action(),
                entry.actorId(),
                auditSourceResolver.current(),
                toJson(entry.before()),
                toJson(entry.after())));
    }

    /** 조회 뷰(G2-2)가 그대로 실어 보낼 표현이라 저장 시점에 문자열로 굳힌다. */
    private String toJson(Map<String, Object> state) {
        if (state == null) {
            return null;
        }

        return jsonMapper.writeValueAsString(state);
    }
}
