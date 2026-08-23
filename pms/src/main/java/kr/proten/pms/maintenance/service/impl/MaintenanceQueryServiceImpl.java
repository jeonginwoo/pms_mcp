package kr.proten.pms.maintenance.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.maintenance.repository.MaintenanceContactRepository;
import kr.proten.pms.maintenance.repository.MaintenanceContractRepository;
import kr.proten.pms.maintenance.repository.MaintenanceIssueRepository;
import kr.proten.pms.maintenance.repository.MaintenanceSiteRepository;
import kr.proten.pms.maintenance.service.MaintenanceQueryService;
import kr.proten.pms.maintenance.service.dto.ContractDetail;
import kr.proten.pms.maintenance.service.dto.ContractQuery;
import kr.proten.pms.maintenance.service.dto.ContractSummary;
import kr.proten.pms.maintenance.service.dto.SiteView;
import kr.proten.pms.maintenance.service.entity.IssueStatus;
import kr.proten.pms.maintenance.service.entity.MaintenanceContact;
import kr.proten.pms.maintenance.service.entity.MaintenanceContract;
import kr.proten.pms.maintenance.service.entity.MaintenanceSite;
import kr.proten.pms.person.PersonRef;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계약 조회 구현 (US-D4).
 *
 * <p>keyword 매칭이 두 질의로 나뉜다: 계약명·계약사는 계약 표에서, 사이트명은 사이트
 * 표에서 계약 id를 먼저 모아 합친다. 한 질의에 join을 섞으면 45사이트 계약이 45행으로
 * 불어나 페이징 총건수가 틀어진다.
 */
@Service
@Transactional(readOnly = true)
class MaintenanceQueryServiceImpl implements MaintenanceQueryService {
    private final MaintenanceContractRepository contractRepository;
    private final MaintenanceSiteRepository siteRepository;
    private final MaintenanceContactRepository contactRepository;
    private final MaintenanceIssueRepository issueRepository;
    private final MaintenanceViewFactory viewFactory;

    MaintenanceQueryServiceImpl(
            MaintenanceContractRepository contractRepository,
            MaintenanceSiteRepository siteRepository,
            MaintenanceContactRepository contactRepository,
            MaintenanceIssueRepository issueRepository,
            MaintenanceViewFactory viewFactory) {
        this.contractRepository = contractRepository;
        this.siteRepository = siteRepository;
        this.contactRepository = contactRepository;
        this.issueRepository = issueRepository;
        this.viewFactory = viewFactory;
    }
    @Override
    public Page<ContractSummary> search(ContractQuery query, Pageable pageable) {
        List<Long> idsBySite = query.hasKeyword()
                ? siteRepository.findContractIdsByNameContaining(query.keyword())
                : List.of();
        // in () 은 DB마다 다르게 구는 표현이라 빈 집합을 넘기지 않는다
        List<Long> safeIds = idsBySite.isEmpty() ? List.of(-1L) : idsBySite;
        Page<MaintenanceContract> page = contractRepository.search(
                query.status() == null ? null : query.status().name(),
                likePattern(query.contractor()),
                query.endedBefore(),
                likePattern(query.keyword()),
                safeIds,
                pageable);
        List<Long> contractIds = page.getContent().stream()
                .map(MaintenanceContract::getId)
                .toList();

        return new PageImpl<>(
                viewFactory.toSummaries(
                        page.getContent(),
                        siteCounts(contractIds),
                        matchedSiteNames(contractIds, query)),
                pageable,
                page.getTotalElements());
    }

    /** like 패턴을 Java에서 만든다 — 질의 안의 concat이 null을 만나면 타입을 잃는다. */
    private static String likePattern(String value) {
        return value == null || value.isBlank()
                ? null
                : "%" + value.trim().toLowerCase(java.util.Locale.ROOT) + "%";
    }

    @Override
    public ContractDetail getContract(long contractId) {
        MaintenanceContract contract =
                contractRepository.findById(contractId).orElseThrow(NotFoundException::new);
        List<SiteView> sites = sitesOf(List.of(contractId));

        return new ContractDetail(
                contract.getId(),
                contract.getSourceProjectId(),
                contract.getContractor(),
                contract.getName(),
                contract.getStatus().label(),
                contract.getSheetSection(),
                contract.getContractDate(),
                contract.getContractDateNote(),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getAmount(),
                contract.getMonthlyAmount(),
                salesRefOf(contract),
                contract.getCategory(),
                contract.getTargetInfra(),
                contract.getRegularCheck(),
                contract.getNote(),
                sites,
                issueCounts(sites),
                contract.getVersion());
    }

    @Override
    public List<SiteView> listSites(long contractId) {
        if (!contractRepository.existsById(contractId)) {
            throw new NotFoundException();
        }

        return sitesOf(List.of(contractId));
    }

    private List<SiteView> sitesOf(List<Long> contractIds) {
        List<MaintenanceSite> sites = siteRepository.findByContractIdInOrderByNameAsc(contractIds);

        if (sites.isEmpty()) {
            return List.of();
        }

        List<MaintenanceContact> contacts = contactRepository.findBySiteIdInOrderByIdAsc(
                sites.stream().map(MaintenanceSite::getId).toList());

        return viewFactory.toSiteViews(sites, contacts);
    }

    /** 상태별 이슈 건수 — 상세에 이슈 행 전체를 싣지 않는다(45사이트 계약이 있다). */
    private Map<String, Long> issueCounts(List<SiteView> sites) {
        if (sites.isEmpty()) {
            return Map.of();
        }

        Map<String, Long> counts = new HashMap<>();

        for (Object[] row : issueRepository.countByStatus(
                sites.stream().map(SiteView::id).toList())) {
            counts.put(((IssueStatus) row[0]).label(), (Long) row[1]);
        }

        return counts;
    }

    private PersonRef salesRefOf(MaintenanceContract contract) {
        if (contract.getSalesRepId() == null) {
            return null;
        }

        return viewFactory.refsOf(List.of(contract.getSalesRepId()))
                .get(contract.getSalesRepId());
    }

    private Map<Long, Integer> siteCounts(List<Long> contractIds) {
        if (contractIds.isEmpty()) {
            return Map.of();
        }

        return siteRepository.findByContractIdInOrderByNameAsc(contractIds).stream()
                .collect(Collectors.groupingBy(
                        MaintenanceSite::getContractId,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }

    /** 무엇 때문에 걸렸는지 — keyword가 없으면 매칭 사이트도 없다. */
    private Map<Long, List<String>> matchedSiteNames(List<Long> contractIds, ContractQuery query) {
        if (!query.hasKeyword() || contractIds.isEmpty()) {
            return Map.of();
        }

        return siteRepository.findMatching(contractIds, query.keyword()).stream()
                .collect(Collectors.groupingBy(
                        MaintenanceSite::getContractId,
                        Collectors.mapping(MaintenanceSite::getName, Collectors.toList())));
    }

}
