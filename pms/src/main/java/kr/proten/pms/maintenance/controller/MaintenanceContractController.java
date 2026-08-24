package kr.proten.pms.maintenance.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.common.web.ApiResponse;
import kr.proten.pms.common.web.PageResponse;
import kr.proten.pms.maintenance.controller.dto.ContractRequest;
import kr.proten.pms.maintenance.controller.dto.SiteRequest;
import kr.proten.pms.maintenance.service.ContractCommandService;
import kr.proten.pms.maintenance.service.MaintenanceQueryService;
import kr.proten.pms.maintenance.service.dto.ContractDetail;
import kr.proten.pms.maintenance.service.dto.ContractQuery;
import kr.proten.pms.maintenance.service.dto.ContractSummary;
import kr.proten.pms.maintenance.service.dto.SiteView;
import kr.proten.pms.maintenance.service.entity.ContractStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 유지보수 계약 조회·쓰기 (US-D4 · US-D2).
 *
 * <p><b>조회만 호출자 id를 받지 않는다</b> — 유지보수 조회는 전사 공개이고 404 은닉도
 * 없다(AC D4-3). 쓰기는 반대다: "계약 관리" 플래그가 없으면 403이므로(D2-3) 화자가
 * 첫 인자다. 한 컨트롤러에 두 성질이 함께 있는 것은 자원이 같기 때문이고, 판정의
 * 갈림은 서비스 계약이 둘이라는 사실로 드러난다
 * ({@code MaintenanceQueryService} · {@code ContractCommandService}).
 *
 * <p>삭제 라우트가 없는 것은 누락이 아니다 — 계약 종료는 상태 {@code 종료}로
 * 표현한다(D2-2, 연 단위 갱신 이력 보존).
 */
@RestController
@RequestMapping("/api/maintenance/contracts")
class MaintenanceContractController {
    private final MaintenanceQueryService maintenanceQueryService;
    private final ContractCommandService contractCommandService;

    MaintenanceContractController(
            MaintenanceQueryService maintenanceQueryService,
            ContractCommandService contractCommandService) {
        this.maintenanceQueryService = maintenanceQueryService;
        this.contractCommandService = contractCommandService;
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

    /** 계약 직접 등록 (AC D2-1) — 이관(D1)과 함께 입구 2개 중 하나다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<ContractDetail> create(
            @CallerPersonId long callerPersonId,
            @Valid @RequestBody ContractRequest request) {
        return ApiResponse.ok(contractCommandService.create(callerPersonId, request.toCommand()));
    }

    /** 계약 수정 (AC D2-2) — version이 어긋나면 409 STALE_VERSION. */
    @PutMapping("/{contractId}")
    ApiResponse<ContractDetail> update(
            @CallerPersonId long callerPersonId,
            @PathVariable long contractId,
            @Valid @RequestBody ContractRequest request) {
        return ApiResponse.ok(contractCommandService.update(
                callerPersonId, contractId, request.toCommand(), request.requiredVersion()));
    }

    /** 사이트 등록 (AC D2-4) — 연락처를 함께 만든다. */
    @PostMapping("/{contractId}/sites")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<SiteView> addSite(
            @CallerPersonId long callerPersonId,
            @PathVariable long contractId,
            @Valid @RequestBody SiteRequest request) {
        return ApiResponse.ok(
                contractCommandService.addSite(callerPersonId, contractId, request.toCommand()));
    }
}
