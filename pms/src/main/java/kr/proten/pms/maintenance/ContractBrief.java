package kr.proten.pms.maintenance;

import java.time.LocalDate;
import java.util.List;

/**
 * 계약 검색 결과 한 건 — MCP {@code ContractSummary}가 그대로 채워지는 모양이다.
 *
 * <p>{@code matchedSites}가 있는 이유: 45사이트 계약이 "가천대길병원"으로 걸렸을 때
 * 무엇 때문에 걸렸는지 보여 주지 않으면 사용자는 계약명만 보고 엉뚱한 결과라고
 * 판단한다. keyword가 없으면 빈 목록이다.
 */
public record ContractBrief(
        long id,
        String contractor,
        String name,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        List<String> matchedSites) {
}
