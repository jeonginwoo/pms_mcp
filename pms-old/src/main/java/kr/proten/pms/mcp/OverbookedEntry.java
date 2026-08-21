package kr.proten.pms.mcp;

import java.util.List;

/** 오버부킹 = 기본 가동률 100% 초과 + 원인 배정 (FR-AI-12 — 2026-08-10 재정의, 구 보정>100 대체) */
public record OverbookedEntry(
        int personId,
        String name,
        String team,
        double basicPct,
        List<Cause> causes) {

    public record Cause(String projectName, double mm) {
    }
}
