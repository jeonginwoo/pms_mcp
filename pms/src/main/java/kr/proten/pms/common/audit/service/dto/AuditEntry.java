package kr.proten.pms.common.audit.service.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.proten.pms.common.audit.service.AuditAction;

/**
 * 기록할 변경 1건 (PRD-pms §4 · G1-1).
 *
 * before·after는 "바뀐 필드만" 담는 스냅샷이다 — 전체 필드를 매번 실으면 무엇이
 * 바뀌었는지 사람이 다시 비교해야 한다. 무엇을 담을지는 도메인이 아는 일이라
 * 기록하는 모듈이 채워 넘긴다.
 *
 * @param projectId 프로젝트 스코프 이벤트만 채운다 — 조직·계정 변경은 null (G2-1)
 * @param before    변경 전 값 — 생성(CREATE)은 null
 * @param after     변경 후 값 — 완전 삭제는 null (소프트 삭제는 상태 변화를 담는다)
 */
public record AuditEntry(
        String entityType,
        Long entityId,
        Long projectId,
        AuditAction action,
        long actorId,
        Map<String, Object> before,
        Map<String, Object> after) {

    public AuditEntry {
        require(entityType != null && !entityType.isBlank(), "entityType은 필수입니다");
        require(entityId != null, "entityId는 필수입니다");
        require(action != null, "action은 필수입니다");
        // 무엇이 바뀐 것인지 알 수 없는 행은 이력이 아니다
        require(before != null || after != null, "before·after가 모두 없으면 기록하지 않습니다");
        before = frozen(before);
        after = frozen(after);
    }

    /**
     * 순서를 유지한 불변 사본 — 스냅샷은 사람이 읽을 표현이라 필드 순서가 의미를 갖고,
     * 기록 후 호출자가 원본 맵을 바꿔도 이력이 흔들리지 않아야 한다.
     */
    private static Map<String, Object> frozen(Map<String, Object> state) {
        if (state == null) {
            return null;
        }

        return Collections.unmodifiableMap(new LinkedHashMap<>(state));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
