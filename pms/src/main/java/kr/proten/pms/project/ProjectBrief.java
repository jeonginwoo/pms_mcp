package kr.proten.pms.project;

import java.time.LocalDate;

/**
 * 프로젝트 목록 항목 — MCP {@code ProjectSummary}가 채워지는 모양이다.
 *
 * <p>{@code team}·{@code division}은 <b>PM의 소속</b>이다(2026-08-23 결정): 프로젝트는
 * 자기 소속을 갖지 않고(PRD §4 — 가시성은 배정 인원이 판정한다), 시드의 프로젝트
 * team·division도 구 익명 명부 PM 소속의 파생값이었다(382/382 일치). PM이 팀을 옮기면
 * 표시도 따라 바뀐다 — "소속 시점이력"은 Out of Scope다.
 */
public record ProjectBrief(
        long id,
        String name,
        String client,
        String status,
        int progress,
        LocalDate startDate,
        LocalDate endDate,
        String team,
        String division) {
}
