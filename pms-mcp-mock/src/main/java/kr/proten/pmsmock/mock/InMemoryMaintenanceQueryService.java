package kr.proten.pmsmock.mock;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import kr.proten.pmsmock.MockData;
import kr.proten.pmsmock.model.MaintenanceContract;
import kr.proten.pmsmock.model.MaintenanceIssue;
import kr.proten.pmsmock.port.MaintenanceQueryService;
import kr.proten.pmsmock.port.ToolError;
import kr.proten.pmsmock.port.dto.ContractSummary;
import kr.proten.pmsmock.port.dto.MaintenanceLogsResult;

public class InMemoryMaintenanceQueryService implements MaintenanceQueryService {

    private static final int MAX_ISSUES = 50; // 서버 절단 (FR-AI-14)
    private static final int MAX_CONTRACTS = 50; // 서버 절단 — 시드 105건 대비 (결정 ④)
    private static final List<String> CONTRACT_STATUSES = List.of("예정", "신규", "유지", "종료"); // PRD-pms §4

    private final MockData data;

    public InMemoryMaintenanceQueryService(MockData data) {
        this.data = data;
    }

    @Override
    public MaintenanceLogsResult listLogs(int callerId, int id, String type) {
        if (type != null && !type.isBlank() && !List.of("장애", "문의", "요청").contains(type)) {
            throw ToolError.validation("type은 장애/문의/요청 중 하나여야 합니다.");
        }
        // 이슈 조회는 전사(D4-3) — 가시성 필터 없음
        Optional<MaintenanceContract> contract = data.contracts.stream()
                .filter(c -> c.id() == id).findFirst();
        if (contract.isPresent()) {
            List<MaintenanceLogsResult.IssueView> issues = data.issues.stream()
                    .filter(i -> i.contractId() == id)
                    .filter(i -> type == null || type.isBlank() || i.type().equals(type))
                    .sorted(Comparator.comparing(MaintenanceIssue::receivedAt).reversed())
                    .limit(MAX_ISSUES)
                    .map(this::toView)
                    .toList();
            return new MaintenanceLogsResult("CONTRACT", id, contract.get().name(), issues);
        }
        MaintenanceIssue issue = data.issues.stream()
                .filter(i -> i.id() == id).findFirst()
                .orElseThrow(ToolError::notFound);
        MaintenanceContract parent = data.contracts.stream()
                .filter(c -> c.id() == issue.contractId()).findFirst().orElseThrow();
        return new MaintenanceLogsResult("ISSUE", parent.id(), parent.name(), List.of(toView(issue)));
    }

    @Override
    public List<ContractSummary> searchContracts(int callerId, String keyword, String status) {
        if (status != null && !status.isBlank() && !CONTRACT_STATUSES.contains(status)) {
            throw ToolError.validation("status는 예정/신규/유지/종료 중 하나여야 합니다.");
        }
        // 검색도 전사(D4-3) — 가시성 판정·404 은닉 없음, 빈 결과는 [] (2026-08-11 결정 ④)
        String kw = keyword == null ? "" : keyword.trim();
        return data.contracts.stream()
                .filter(c -> status == null || status.isBlank() || c.status().equals(status))
                .filter(c -> kw.isEmpty() || c.name().contains(kw) || c.client().contains(kw)
                        || c.siteName().contains(kw))
                .sorted(Comparator.comparing(MaintenanceContract::endDate).reversed())
                .limit(MAX_CONTRACTS)
                .map(c -> new ContractSummary(c.id(), c.name(), c.client(), c.status(),
                        c.startDate(), c.endDate(),
                        !kw.isEmpty() && c.siteName().contains(kw) ? List.of(c.siteName()) : List.of()))
                .toList();
    }

    private MaintenanceLogsResult.IssueView toView(MaintenanceIssue i) {
        List<MaintenanceLogsResult.CommentView> comments = i.comments().stream()
                .map(c -> new MaintenanceLogsResult.CommentView(c.date(), data.person(c.authorId()).name(), c.text()))
                .toList();
        String assignee = i.assigneeId() == null ? null : data.person(i.assigneeId()).name();
        return new MaintenanceLogsResult.IssueView(i.id(), i.type(), i.status(), i.title(),
                i.receivedAt(), assignee, comments);
    }
}
