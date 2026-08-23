package kr.proten.pms.maintenance.service.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 계약 목록 항목 (AC D4-1) — MCP {@code search_maintenance}의 응답과 같은 구성이다
 * (계약 id·계약명·계약사·상태·기간·매칭 사이트).
 *
 * <p>{@code matchedSites}가 있는 이유: 45사이트 계약이 "가천대길병원"으로 걸렸을 때
 * 무엇 때문에 걸렸는지 보여 주지 않으면 사용자는 계약명만 보고 엉뚱한 결과라고
 * 판단한다. keyword가 없으면 빈 목록이다.
 */
public record ContractSummary(
        long id,
        String contractor,
        String name,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        int siteCount,
        List<String> matchedSites) {
}
