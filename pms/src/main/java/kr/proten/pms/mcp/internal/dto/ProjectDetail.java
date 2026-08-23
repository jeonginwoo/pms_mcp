package kr.proten.pms.mcp.internal.dto;

import java.util.List;

/**
 * 프로젝트 상세 — version 포함(FR-AI-10, 쓰기 시나리오가 조회만으로 완결).
 * myRole 미포함 — 불포함 확정(2026-08-12 결정. B2-1 근거: 역할 확인 헤맴 0).
 */
public record ProjectDetail(
        int id,
        String name,
        String client,
        String status,
        int progress,
        String startDate,
        String endDate,
        double contractMm,
        String engagement,
        String solution,
        String pm,
        List<String> participants,
        String team,
        String division,
        int version) {
}
