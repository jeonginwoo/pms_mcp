package kr.proten.pms.mcp;

/**
 * 가동률 (상위 PRD §3 산식 — 2026-08-10 재정의):
 * 기본 = Σ배정MM ÷ 가용MM (과부하 판정·집계의 정본) ·
 * 보정 = Σ(배정MM × 직급계수) ÷ 가용MM (단가 가중 보조 지표 — 과부하 판정에 쓰지 않음).
 * team·division 동봉 — 집계 결과를 소속별로 정리할 수 있게 (2026-08-11 결정 ③, C1-6과 동일 계약)
 */
public record UtilizationEntry(
        int personId,
        String name,
        String team,
        String division,
        String month,
        double assignedMm,
        double capacityMm,
        double basicPct,
        double adjustedPct) {
}
