package kr.proten.pms.audit;

import kr.proten.pms.audit.AuditEntry;

/**
 * 감사 기록 계약 (PRD-pms EPIC G) — 변경 1건 = 로그 1행(G1-1).
 *
 * 수정·삭제 메서드가 없는 것이 append-only(G1-2)의 구현이다. 조회 뷰(통합 G1-3 ·
 * 프로젝트별 G2-2)는 아직 없다 — 2026-08-21 결정: 나중에 되돌릴 수 없는 것은
 * "그때 남기지 않은 이력"이고 조회 화면은 언제든 얹을 수 있으므로 기록을 먼저 쌓는다.
 */
public interface AuditTrail {

    /**
     * 변경 1건을 기록한다.
     * 호출자의 트랜잭션에 참여하므로 변경이 롤백되면 이 행도 함께 사라진다 —
     * 일어나지 않은 변경의 이력은 남지 않아야 한다.
     */
    void record(AuditEntry entry);
}
