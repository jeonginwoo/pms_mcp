package kr.proten.pms.mcp.internal.dto;

import java.util.List;

/**
 * search_maintenance 응답 항목 (2026-08-11 결정 ④): 계약 id·계약명·계약사·상태·기간·매칭 사이트.
 * matchedSites = keyword가 사이트명으로 매칭됐을 때 그 사이트들 — 사용자가 부른 이름(고객사)과
 * 계약의 연결 근거를 보여준다. 계약명·계약사 매칭이면 빈 목록.
 */
public record ContractSummary(
        int contractId,
        String name,
        String client,
        String status,
        String startDate,
        String endDate,
        List<String> matchedSites) {
}
