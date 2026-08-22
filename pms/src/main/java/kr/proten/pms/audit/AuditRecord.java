package kr.proten.pms.audit;

import java.time.Instant;
import java.util.Map;
import kr.proten.pms.audit.AuditAction;
import kr.proten.pms.audit.AuditSource;

/**
 * 조회로 나가는 감사 1행 (AC G1-3·G2-2).
 *
 * `AuditEntry`(쓰기 입력)와 따로 두는 이유: 쓰기는 "무엇을 남길지"를 도메인이
 * 채워 넣는 값이고 조회는 id·시각·source처럼 **기록된 뒤에야 존재하는** 값을 함께
 * 담는다. 한 record로 합치면 쓰기 쪽이 채울 수 없는 필드를 들고 다니게 된다.
 *
 * 통합 로그(G1-3)와 프로젝트별 이력(G2-2)이 같은 형태인 것은 의도다 —
 * 저장이 한 테이블이므로 두 뷰는 필터만 다르다.
 */
public record AuditRecord(
        long id,
        String entityType,
        Long entityId,
        Long projectId,
        AuditAction action,
        long actorId,
        AuditSource source,
        Map<String, Object> before,
        Map<String, Object> after,
        Instant createdAt) {
}
