package kr.proten.pmsmock.port.dto;

import java.util.List;

/** 오버부킹 = 보정 가동률 100% 초과 + 원인 배정 (FR-AI-12) */
public record OverbookedEntry(
        int personId,
        String name,
        String team,
        double adjustedPct,
        List<Cause> causes) {

    public record Cause(String projectName, double mm) {
    }
}
