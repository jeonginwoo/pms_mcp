package kr.proten.pms.maintenance.service.impl;

import java.util.List;
import java.util.Optional;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.maintenance.CommentBrief;
import kr.proten.pms.maintenance.ContractBrief;
import kr.proten.pms.maintenance.ContractIssues;
import kr.proten.pms.maintenance.IssueBrief;
import kr.proten.pms.maintenance.MaintenanceLookupService;
import kr.proten.pms.maintenance.service.IssueQueryService;
import kr.proten.pms.maintenance.service.MaintenanceQueryService;
import kr.proten.pms.maintenance.service.dto.ContractQuery;
import kr.proten.pms.maintenance.service.dto.ContractSummary;
import kr.proten.pms.maintenance.service.dto.IssueView;
import kr.proten.pms.maintenance.service.entity.ContractStatus;
import kr.proten.pms.maintenance.service.entity.IssueType;
import kr.proten.pms.person.PersonRef;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link MaintenanceLookupService} 구현 — 모듈 내부 조회를 어댑터가 쓰는 모양으로 좁힌다.
 *
 * <p>질의를 새로 쓰지 않고 내부 계약을 그대로 부른다: 같은 질문에 두 질의를 두면
 * 필터 규칙(keyword 3종 매칭·정렬)이 갈라진다. 이 클래스가 하는 일은 라벨 → enum
 * 해석과 페이지 → 목록 절단뿐이다.
 */
@Service
@Transactional(readOnly = true)
class MaintenanceLookupServiceImpl implements MaintenanceLookupService {
    private final MaintenanceQueryService contracts;
    private final IssueQueryService issues;

    MaintenanceLookupServiceImpl(MaintenanceQueryService contracts, IssueQueryService issues) {
        this.contracts = contracts;
        this.issues = issues;
    }

    @Override
    public List<ContractBrief> searchContracts(String keyword, String statusLabel, int limit) {
        ContractQuery query = new ContractQuery(statusOf(statusLabel), null, null, keyword);

        return contracts.search(query, PageRequest.of(0, limit)).getContent().stream()
                .map(MaintenanceLookupServiceImpl::toBrief)
                .toList();
    }

    @Override
    public Optional<ContractIssues> logsOf(long id, String typeLabel, int limit) {
        IssueType type = typeOf(typeLabel);

        // 계약이 우선이다 — 도구가 "계약 id면 소속 이슈 전체"를 앞세웠고 목업도 같은
        // 순서다. 예외로 갈래를 가르지 않는다: 트랜잭션 안에서 잡은 예외는 그 트랜잭션을
        // 롤백 대상으로 표시해 뒤 질의가 조용히 망가진다(실측).
        if (contracts.contractExists(id)) {
            List<IssueView> found = issues.listByContract(id, type, PageRequest.of(0, limit));

            return Optional.of(new ContractIssues(
                    "CONTRACT",
                    id,
                    found.isEmpty() ? contracts.contractName(id) : found.getFirst().contractName(),
                    found.stream().map(MaintenanceLookupServiceImpl::toBrief).toList()));
        }

        return issues.findIssue(id).map(issue -> new ContractIssues(
                "ISSUE",
                issue.contractId(),
                issue.contractName(),
                type != null && !type.label().equals(issue.type())
                        ? List.of()
                        : List.of(toBrief(issue))));
    }

    private static ContractBrief toBrief(ContractSummary summary) {
        return new ContractBrief(
                summary.id(),
                summary.contractor(),
                summary.name(),
                summary.status(),
                summary.startDate(),
                summary.endDate(),
                summary.matchedSites());
    }

    private static IssueBrief toBrief(IssueView issue) {
        return new IssueBrief(
                issue.id(),
                issue.type(),
                issue.status(),
                issue.title(),
                issue.receivedAt(),
                nameOf(issue.assignee()),
                issue.siteName(),
                issue.comments().stream()
                        .map(comment -> new CommentBrief(
                                comment.createdAt() == null
                                        ? null
                                        : comment.createdAt()
                                                .atZone(java.time.ZoneId.systemDefault())
                                                .toLocalDate(),
                                nameOf(comment.author()),
                                comment.content()))
                        .toList());
    }

    private static String nameOf(PersonRef person) {
        return person == null ? null : person.name();
    }

    /** 모르는 라벨은 예외다 — 조용히 "필터 없음"으로 바꾸면 사용자가 틀린 답을 받는다. */
    private static ContractStatus statusOf(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }

        for (ContractStatus candidate : ContractStatus.values()) {
            if (candidate.label().equals(label.trim())) {
                return candidate;
            }
        }

        throw new ValidationException("계약 상태는 예정/신규/유지/종료 중 하나여야 합니다", "status");
    }

    private static IssueType typeOf(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }

        for (IssueType candidate : IssueType.values()) {
            if (candidate.label().equals(label.trim())) {
                return candidate;
            }
        }

        throw new ValidationException("이슈 유형은 장애/문의/요청 중 하나여야 합니다", "type");
    }
}
