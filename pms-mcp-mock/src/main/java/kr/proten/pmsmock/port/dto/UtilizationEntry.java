package kr.proten.pmsmock.port.dto;

/** 가동률 (상위 PRD §3 산식 — 기본 = Σ배정MM/가용MM, 보정 = Σ배정MM/(가용MM×직급계수)) */
public record UtilizationEntry(
        int personId,
        String name,
        String month,
        double assignedMm,
        double capacityMm,
        double basicPct,
        double adjustedPct) {
}
