package kr.proten.pms.maintenance.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.maintenance.repository.IssueCommentRepository;
import kr.proten.pms.maintenance.repository.MaintenanceContractRepository;
import kr.proten.pms.maintenance.repository.MaintenanceIssueRepository;
import kr.proten.pms.maintenance.repository.MaintenanceSiteRepository;
import kr.proten.pms.maintenance.service.IssueQueryService;
import kr.proten.pms.maintenance.service.dto.IssueQuery;
import kr.proten.pms.maintenance.service.dto.IssueView;
import kr.proten.pms.maintenance.service.entity.IssueComment;
import kr.proten.pms.maintenance.service.entity.IssueType;
import kr.proten.pms.maintenance.service.entity.MaintenanceIssue;
import kr.proten.pms.maintenance.service.entity.MaintenanceSite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이슈 조회 구현 (AC D3-4 · MCP {@code list_maintenance_logs}).
 *
 * <p>계약에서 이슈로 가는 길은 사이트를 거친다(계약 → 사이트 → 이슈): 이슈는 사이트에
 * 붙고 사이트가 계약에 붙는다. 그 두 단계를 호출자가 밟게 하면 같은 질의가 화면과
 * {@code /mcp} 어댑터에 두 벌 생긴다.
 */
@Service
@Transactional(readOnly = true)
class IssueQueryServiceImpl implements IssueQueryService {
    private final MaintenanceIssueRepository issueRepository;
    private final MaintenanceSiteRepository siteRepository;
    private final MaintenanceContractRepository contractRepository;
    private final IssueCommentRepository commentRepository;
    private final IssueViewFactory viewFactory;

    IssueQueryServiceImpl(
            MaintenanceIssueRepository issueRepository,
            MaintenanceSiteRepository siteRepository,
            MaintenanceContractRepository contractRepository,
            IssueCommentRepository commentRepository,
            IssueViewFactory viewFactory) {
        this.issueRepository = issueRepository;
        this.siteRepository = siteRepository;
        this.contractRepository = contractRepository;
        this.commentRepository = commentRepository;
        this.viewFactory = viewFactory;
    }

    @Override
    public Page<IssueView> search(IssueQuery query, Pageable pageable) {
        List<Long> siteIds = query.contractId() == null ? null : siteIdsOf(query.contractId());

        if (siteIds != null && siteIds.isEmpty()) {
            // 사이트 없는 계약으로 걸렀다 — 질의하지 않고 빈 페이지다
            return Page.empty(pageable);
        }

        Page<MaintenanceIssue> page = issueRepository.search(
                query.status() == null ? null : query.status().name(),
                query.type() == null ? null : query.type().name(),
                query.siteId(),
                query.assigneeId(),
                query.unassignedOnly(),
                siteIds == null,
                siteIds == null ? List.of(-1L) : siteIds,
                pageable);

        return new PageImpl<>(toViews(page.getContent()), pageable, page.getTotalElements());
    }

    @Override
    public IssueView getIssue(long issueId) {
        MaintenanceIssue issue =
                issueRepository.findById(issueId).orElseThrow(NotFoundException::new);

        return toViews(List.of(issue)).getFirst();
    }


    @Override
    public Optional<IssueView> findIssue(long issueId) {
        return issueRepository.findById(issueId)
                .map(issue -> toViews(List.of(issue)).getFirst());
    }

    @Override
    public List<IssueView> listByContract(long contractId, IssueType type, Pageable pageable) {
        if (!contractRepository.existsById(contractId)) {
            throw new NotFoundException();
        }

        List<Long> siteIds = siteIdsOf(contractId);

        if (siteIds.isEmpty()) {
            return List.of();
        }

        return toViews(issueRepository.findBySiteIds(
                siteIds, type == null ? null : type.name(), pageable));
    }

    /**
     * 이슈 목록에 사이트·계약명·담당자·코멘트를 붙인다. 이슈마다 되묻지 않고
     * <b>한 번에 모아</b> 온다 — 최근 50건 목록이 50번의 추가 질의가 되면 안 된다.
     */
    private List<IssueView> toViews(List<MaintenanceIssue> issues) {
        if (issues.isEmpty()) {
            return List.of();
        }

        List<Long> siteIds = issues.stream()
                .map(MaintenanceIssue::getSiteId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, MaintenanceSite> sites = siteIds.isEmpty()
                ? Map.of()
                : siteRepository.findAllById(siteIds).stream()
                        .collect(Collectors.toMap(MaintenanceSite::getId, site -> site));
        Map<Long, String> contractNames = contractNamesOf(sites.values());
        Map<Long, List<IssueComment>> comments =
                commentRepository
                        .findByIssueIdInOrderByCreatedAtAscIdAsc(
                                issues.stream().map(MaintenanceIssue::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(
                                IssueComment::getIssueId));

        return viewFactory.toViews(issues, sites, contractNames, comments);
    }

    private Map<Long, String> contractNamesOf(Collection<MaintenanceSite> sites) {
        List<Long> contractIds =
                sites.stream().map(MaintenanceSite::getContractId).distinct().toList();

        if (contractIds.isEmpty()) {
            return Map.of();
        }

        return contractRepository.findByIdIn(contractIds).stream()
                .collect(Collectors.toMap(
                        contract -> contract.getId(),
                        contract -> contract.getName()));
    }

    private List<Long> siteIdsOf(long contractId) {
        return siteRepository.findByContractIdOrderByNameAsc(contractId).stream()
                .map(MaintenanceSite::getId)
                .toList();
    }

    /** 도구가 약속한 "최근 50건" 절단 — 호출자가 페이지 크기를 주지 않을 때의 기본값. */
    static Pageable defaultToolPage() {
        return PageRequest.of(0, 50);
    }
}
