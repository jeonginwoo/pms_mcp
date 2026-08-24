package kr.proten.pms.maintenance.controller;

import jakarta.validation.Valid;
import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.common.web.ApiResponse;
import kr.proten.pms.maintenance.controller.dto.SiteRequest;
import kr.proten.pms.maintenance.service.ContractCommandService;
import kr.proten.pms.maintenance.service.dto.SiteView;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사이트 수정 (AC D2-4).
 *
 * <p>계약 컨트롤러와 나뉘는 이유는 <b>경로가 계약 밑이 아니기 때문</b>이다: 등록은
 * 계약을 지정해야 하므로 {@code /contracts/{id}/sites}지만, 수정은 사이트 id 하나로
 * 지정되고 §7이 {@code PUT /sites/{id}}로 적어 뒀다. project가 배정을
 * {@code POST /projects/{id}/assignments} · {@code PUT /assignments/{id}}로 가른 것과
 * 같은 모양이다 — 하위 자원이 자기 id를 얻으면 그때부터 최상위 경로로 다룬다.
 *
 * <p>삭제 라우트는 없다: AC에 없다.
 */
@RestController
@RequestMapping("/api/maintenance/sites")
class MaintenanceSiteController {
    private final ContractCommandService contractCommandService;

    MaintenanceSiteController(ContractCommandService contractCommandService) {
        this.contractCommandService = contractCommandService;
    }

    /** 사이트 수정 (AC D2-4) — 연락처는 전체 교체이고 version이 어긋나면 409. */
    @PutMapping("/{siteId}")
    ApiResponse<SiteView> update(
            @CallerPersonId long callerPersonId,
            @PathVariable long siteId,
            @Valid @RequestBody SiteRequest request) {
        return ApiResponse.ok(contractCommandService.updateSite(
                callerPersonId, siteId, request.toCommand(), request.requiredVersion()));
    }
}
