package kr.proten.pms.maintenance.controller;

import jakarta.validation.Valid;
import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.common.web.ApiResponse;
import kr.proten.pms.common.web.PageResponse;
import kr.proten.pms.maintenance.controller.dto.CommentRequest;
import kr.proten.pms.maintenance.controller.dto.IssueEditRequest;
import kr.proten.pms.maintenance.controller.dto.IssueRequest;
import kr.proten.pms.maintenance.service.IssueCommandService;
import kr.proten.pms.maintenance.service.IssueQueryService;
import kr.proten.pms.maintenance.service.dto.CommentView;
import kr.proten.pms.maintenance.service.dto.IssueQuery;
import kr.proten.pms.maintenance.service.dto.IssueView;
import kr.proten.pms.maintenance.service.entity.IssueStatus;
import kr.proten.pms.maintenance.service.entity.IssueType;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 유지보수 이슈 조회·쓰기 (AC D3-4 · US-D3 쓰기 3종).
 *
 * <p><b>조회도 쓰기도 화자 판정이 갈리지 않는다</b> — 유지보수 조회는 전사 공개이고
 * (D4-3) 이슈 쓰기는 로그인 사용자 전체다(US-D3). 계약 컨트롤러가 조회·쓰기로
 * 갈라지는 것과 다른 자리이고, 그 차이는 서비스 계약이 관문을 갖는지로 드러난다
 * ({@code ContractCommandService}는 {@code ContractWriteGuard}를 지나고 이슈는 아니다).
 *
 * <p>화자 id를 받는 것은 판정 때문이 아니라 <b>기록</b> 때문이다 — 감사의 행위자와
 * 코멘트의 작성자다.
 *
 * <p>{@code unassigned=true}가 {@code assigneeId}와 별개인 이유: 담당자 파라미터를
 * 비우는 것은 "담당자로 거르지 않는다"이고, 미배정만 보는 것은 다른 요청이다.
 * "내 담당 열린 이슈"와 "아무도 안 보는 이슈"가 둘 다 한 번의 조회여야 한다.
 */
@RestController
@RequestMapping("/api/maintenance/issues")
class MaintenanceIssueController {
    private final IssueQueryService issueQueryService;
    private final IssueCommandService issueCommandService;

    MaintenanceIssueController(
            IssueQueryService issueQueryService, IssueCommandService issueCommandService) {
        this.issueQueryService = issueQueryService;
        this.issueCommandService = issueCommandService;
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

    /** 이슈 등록 (AC D3-1) — 담당자는 사이트의 담당 엔지니어가 된다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<IssueView> register(
            @CallerPersonId long callerPersonId,
            @Valid @RequestBody IssueRequest request) {
        return ApiResponse.ok(issueCommandService.register(callerPersonId, request.toCommand()));
    }

    /**
     * 이슈 처리 (AC D3-2) — 상태 전이·담당 재배정.
     * {@code PUT}이 아니라 {@code PATCH}인 것은 두 칸이 서로 독립이기 때문이다:
     * 상태만 바꾸는 요청에 담당자를 함께 실으라고 요구할 이유가 없다.
     */
    @PatchMapping("/{issueId}")
    ApiResponse<IssueView> process(
            @CallerPersonId long callerPersonId,
            @PathVariable long issueId,
            @Valid @RequestBody IssueEditRequest request) {
        return ApiResponse.ok(issueCommandService.process(
                callerPersonId, issueId, request.toCommand(), request.requiredVersion()));
    }

    /** 코멘트 추가 (AC D3-3) — append-only라 수정·삭제 라우트가 없다. */
    @PostMapping("/{issueId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<CommentView> addComment(
            @CallerPersonId long callerPersonId,
            @PathVariable long issueId,
            @Valid @RequestBody CommentRequest request) {
        return ApiResponse.ok(
                issueCommandService.addComment(callerPersonId, issueId, request.content()));
    }
}
