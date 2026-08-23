package kr.proten.pms.maintenance.service.dto;

import java.time.LocalDate;
import kr.proten.pms.maintenance.service.entity.ContractStatus;

/**
 * 계약 목록 필터 (AC D4-1) — 전부 선택이고 null은 "그 조건을 보지 않는다"다.
 *
 * @param keyword 계약명·계약사·사이트명 부분 일치 (2026-08-11 결정 — 사이트명이
 *                45사이트 계약에 도달하는 유일한 경로다)
 * @param endedBefore 종료일이 이 날짜 이전 — 만료 임박·지난 계약을 훑는 용도
 */
public record ContractQuery(
        ContractStatus status, String contractor, LocalDate endedBefore, String keyword) {

    public static ContractQuery all() {
        return new ContractQuery(null, null, null, null);
    }

    /** 사이트명 축을 질의할 필요가 있는가 — keyword가 없으면 사이트를 훑지 않는다. */
    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }
}
