package kr.proten.pmsmock.port.dto;

import java.util.List;

/**
 * 프로젝트 상세 — version 포함(FR-AI-10, 쓰기 시나리오가 조회만으로 완결).
 * myRole 미포함 — M-1 실험 후 포함 여부 결정(2026-08-03 유예).
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
