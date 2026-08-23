package kr.proten.pms.maintenance.controller;

import kr.proten.pms.common.web.ApiResponse;
import kr.proten.pms.common.web.PageResponse;
import kr.proten.pms.maintenance.service.IssueQueryService;
import kr.proten.pms.maintenance.service.dto.IssueQuery;
import kr.proten.pms.maintenance.service.dto.IssueView;
import kr.proten.pms.maintenance.service.entity.IssueStatus;
import kr.proten.pms.maintenance.service.entity.IssueType;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 유지보수 이슈 조회 (AC D3-4).
 *
 * <p>{@code unassigned=true}가 {@code assigneeId}와 별개인 이유: 담당자 파라미터를
 * 비우는 것은 "담당자로 거르지 않는다"이고, 미배정만 보는 것은 다른 요청이다.
 * "내 담당 열린 이슈"와 "아무도 안 보는 이슈"가 둘 다 한 번의 조회여야 한다.
 */
@RestController
@RequestMapping("/api/maintenance/issues")
class MaintenanceIssueController {
    private final IssueQueryService issueQueryService;

    MaintenanceIssueController(IssueQueryService issueQueryService) {
        this.issueQueryService = issueQueryService;
    }

    @GetMapping
    ApiResponse<PageResponse<IssueView>> list(
            @RequestParam(required = false) IssueStatus status,
            @RequestParam(required = false) IssueType type,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(defaultValue = "false") boolean unassigned,
            Pageable pageable) {
        IssueQuery query =
                new IssueQuery(status, type, siteId, assigneeId, unassigned, contractId);

        return ApiResponse.ok(PageResponse.of(issueQueryService.search(query, pageable)));
    }

    /** 이슈 단건 — 코멘트를 함께 싣는다. */
    @GetMapping("/{issueId}")
    ApiResponse<IssueView> get(@PathVariable long issueId) {
        return ApiResponse.ok(issueQueryService.getIssue(issueId));
    }
}
