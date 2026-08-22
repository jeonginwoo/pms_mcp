package kr.proten.pms.resource.service.dto;

import java.time.YearMonth;

/**
 * 가동률 조회 조건 (§7 `GET /api/utilization?month=&personId=&orgUnitId=&overbooked=`).
 *
 * 셋을 한 값으로 묶는 이유: 개인 지정(personId)과 집계(orgUnitId·전사)는 **모집단
 * 규칙이 다르다**(C1-5 — billable=false 제외는 집계에만 적용, 개인 지정은 무관).
 * 파라미터가 흩어져 있으면 그 규칙이 호출 지점마다 다시 판단된다.
 *
 * @param month        기준 월 — 필수
 * @param personId     개인 지정 조회 — null이면 집계
 * @param orgUnitId    집계 범위(subtree) — null이면 가시성 범위 전체
 * @param overbookedOnly 기본 가동률 100 초과인 사람만 (C1-3 — 판정 기준은 기본, 보정 아님)
 */
public record UtilizationQuery(
        YearMonth month,
        Long personId,
        Long orgUnitId,
        boolean overbookedOnly) {

    public UtilizationQuery {
        if (month == null) {
            throw new IllegalArgumentException("기준 월은 필수입니다");
        }
    }

    /** 개인 지정 조회인가 — 집계 모집단 규칙(C1-5)이 적용되지 않는 경우다. */
    public boolean isSinglePerson() {
        return personId != null;
    }
}
