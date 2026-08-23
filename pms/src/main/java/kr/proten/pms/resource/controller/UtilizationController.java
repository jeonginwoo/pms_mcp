package kr.proten.pms.resource.controller;

import java.time.YearMonth;
import java.util.List;
import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.common.web.ApiResponse;
import kr.proten.pms.resource.service.UtilizationQueryService;
import kr.proten.pms.resource.service.dto.UtilizationQuery;
import kr.proten.pms.resource.service.dto.UtilizationView;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가동률 API (PRD-pms §7 `GET /api/utilization`) — EPIC C.
 *
 * 목록이지만 page 봉투가 아니다: 한 달 가동률은 가시성 범위 인원 전체를 한 화면에
 * 놓고 비교하는 값이라(집계·과부하 판정) 페이지로 잘리면 쓸모가 줄어든다.
 * 규모는 시드 기준 44명이다.
 */
@RestController
class UtilizationController {
    private final UtilizationQueryService utilizationQueryService;

    UtilizationController(UtilizationQueryService utilizationQueryService) {
        this.utilizationQueryService = utilizationQueryService;
    }

    /**
     * 가동률 조회 (AC C1-1).
     * `personId`가 있으면 개인 지정, 없으면 집계다 — 모집단 규칙이 갈린다(C1-5).
     */
    @GetMapping("/api/utilization")
    ApiResponse<List<UtilizationView>> find(
            @CallerPersonId long callerPersonId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) Long personId,
            @RequestParam(required = false) Long orgUnitId,
            @RequestParam(required = false, defaultValue = "false") boolean overbooked) {
        return ApiResponse.ok(utilizationQueryService.find(callerPersonId,
                new UtilizationQuery(month, personId, orgUnitId, overbooked)));
    }
}
