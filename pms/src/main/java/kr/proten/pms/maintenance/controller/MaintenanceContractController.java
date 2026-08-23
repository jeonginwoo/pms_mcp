package kr.proten.pms.maintenance.controller;

import java.time.LocalDate;
import java.util.List;
import kr.proten.pms.common.web.ApiResponse;
import kr.proten.pms.common.web.PageResponse;
import kr.proten.pms.maintenance.service.MaintenanceQueryService;
import kr.proten.pms.maintenance.service.dto.ContractDetail;
import kr.proten.pms.maintenance.service.dto.ContractQuery;
import kr.proten.pms.maintenance.service.dto.ContractSummary;
import kr.proten.pms.maintenance.service.dto.SiteView;
import kr.proten.pms.maintenance.service.entity.ContractStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 유지보수 계약 조회 (US-D4).
 *
 * <p>호출자 id를 받지 않는다 — 유지보수 조회는 전사 공개이고 404 은닉도 없다
 * (AC D4-3). 로그인은 여전히 필요하지만(§7 인증) 판정에 화자가 쓰이지 않으므로
 * 파라미터로 들이지 않는다.
 */
@RestController
@RequestMapping("/api/maintenance/contracts")
class MaintenanceContractController {
    private final MaintenanceQueryService maintenanceQueryService;

    MaintenanceContractController(MaintenanceQueryService maintenanceQueryService) {
        this.maintenanceQueryService = maintenanceQueryService;
    }

    /** 계약 목록 (D4-1) — keyword는 계약명·계약사·사이트명 3종 부분 일치. */
    @GetMapping
    ApiResponse<PageResponse<ContractSummary>> list(
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(required = false) String contractor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endedBefore,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        ContractQuery query = new ContractQuery(status, contractor, endedBefore, keyword);

        return ApiResponse.ok(
                PageResponse.of(maintenanceQueryService.search(query, pageable)));
    }

    /** 계약 상세 (D4-2) — 사이트·연락처·이슈 요약·원 프로젝트 링크. */
    @GetMapping("/{contractId}")
    ApiResponse<ContractDetail> get(@PathVariable long contractId) {
        return ApiResponse.ok(maintenanceQueryService.getContract(contractId));
    }

    /** 계약의 사이트 목록 (§7 라우트) — 담당 엔지니어 포함. */
    @GetMapping("/{contractId}/sites")
    ApiResponse<List<SiteView>> listSites(@PathVariable long contractId) {
        return ApiResponse.ok(maintenanceQueryService.listSites(contractId));
    }
}
