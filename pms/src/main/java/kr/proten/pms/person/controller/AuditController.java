package kr.proten.pms.person.controller;

import kr.proten.pms.audit.AuditRecord;
import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.common.web.ApiResponse;
import kr.proten.pms.common.web.PageResponse;
import kr.proten.pms.person.controller.dto.*;
import kr.proten.pms.person.service.AuditViewService;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통합 감사 로그 API (PRD-pms §7 `GET /api/audit`) — AC G1-3.
 *
 * person 모듈에 있는 이유는 판정자가 "사용자/조직/권한 관리" 플래그이기 때문이다.
 * 프로젝트별 이력(G2-2)은 판정 기준이 가시성이라 project 모듈에 따로 있다 —
 * 저장은 하나지만 뷰가 둘인 것은 권한이 둘이기 때문이다.
 */
@RestController
class AuditController {
    private final AuditViewService auditViewService;

    AuditController(AuditViewService auditViewService) {
        this.auditViewService = auditViewService;
    }

    @GetMapping("/api/audit")
    ApiResponse<PageResponse<AuditRecord>> list(
            @CallerPersonId long callerPersonId, Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(auditViewService.listAll(callerPersonId, pageable)));
    }
}
