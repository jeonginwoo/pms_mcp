package kr.proten.pms.resource.service.dto;

import java.time.YearMonth;

/**
 * 한 사람의 한 달 가동률 (AC C1-1·C1-6).
 *
 * `team`·`division`을 담는 이유: 집계 결과를 소속별로 정리하려면 필요한데, 없으면
 * 호출자가 인원 수만큼 개인 조회를 반복하게 된다 (C1-6 — MCP `get_utilization`
 * 응답과 같은 형태).
 *
 * @param assignedMm 그 달 배정 M/M 합 — **실투입 계획**이지 계약 배분이 아니다(2026-08-10)
 * @param basic      기본 = Σ배정MM ÷ 가용 × 100 — 오버부킹·집계의 정본(C1-3)
 * @param adjusted   보정 = Σ(배정MM × coeff) ÷ 가용 × 100 — 단가 가중 보조 지표
 */
public record UtilizationView(
        long personId,
        String name,
        String team,
        String division,
        YearMonth month,
        double assignedMm,
        double availableMm,
        double basic,
        double adjusted) {

    /** 과부하 여부 — 판정은 언제나 기본 가동률이다 (C1-3, 2026-08-10 재정의). */
    public boolean overbooked() {
        return basic > 100.0;
    }
}
