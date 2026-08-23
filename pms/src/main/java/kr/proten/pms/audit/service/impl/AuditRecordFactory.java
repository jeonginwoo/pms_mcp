package kr.proten.pms.audit.service.impl;

import java.util.Map;
import kr.proten.pms.audit.AuditRecord;
import kr.proten.pms.audit.service.entity.AuditLog;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link AuditLog} → {@link AuditRecord} 변환 — 스냅샷 JSON을 다시 맵으로 편다.
 *
 * <p>{@link AuditTrailImpl}과 <b>같은 {@code JsonMapper}</b>를 쓴다: 쓰는 쪽과 읽는
 * 쪽이 다른 설정을 쓰면 저장은 성공하고 조회만 깨지는 형태로 이력이 어긋난다.
 *
 * <p>깨진 JSON에 <b>조회 전체를 실패시키지 않는다</b>: 감사 로그는 append-only라
 * 고칠 수 없고(G1-2), 한 행의 스냅샷이 상하면 그 행만 값 없이 보이는 것이
 * "이력 화면이 열리지 않는다"보다 낫다. 어느 행이 상했는지는 id로 남는다.
 */
@Component
class AuditRecordFactory {
    private static final TypeReference<Map<String, Object>> SNAPSHOT = new TypeReference<>() {
    };

    private final JsonMapper jsonMapper;

    AuditRecordFactory(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    AuditRecord toRecord(AuditLog log) {
        return new AuditRecord(
                log.getId(),
                log.getEntityType(),
                log.getEntityId(),
                log.getProjectId(),
                log.getAction(),
                log.getActorId(),
                log.getSource(),
                toSnapshot(log.getBeforeState()),
                toSnapshot(log.getAfterState()),
                log.getCreatedAt());
    }

    /** CREATE의 before처럼 원래 없는 스냅샷은 null 그대로 둔다 — 빈 맵과 구분된다. */
    private Map<String, Object> toSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            return jsonMapper.readValue(json, SNAPSHOT);
        } catch (RuntimeException cause) {
            // Jackson 3의 예외는 언체크다 — 한 행 때문에 목록을 세우지 않는다
            return null;
        }
    }
}
