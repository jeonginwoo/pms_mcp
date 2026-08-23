package kr.proten.pms.maintenance.service;

import java.util.List;
import kr.proten.pms.maintenance.service.dto.IssueQuery;
import kr.proten.pms.maintenance.service.dto.IssueView;
import kr.proten.pms.maintenance.service.entity.IssueType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 유지보수 이슈 조회 (AC D3-4 · MCP {@code list_maintenance_logs}).
 *
 * <p>{@link MaintenanceQueryService}와 마찬가지로 가시성 판정이 없다(D4-3 전사 공개).
 *
 * <p>{@code listByContract}가 따로 있는 이유: 도구가 "계약 id면 소속 이슈 전체"를
 * 약속했는데, 계약에서 이슈로 가는 길은 사이트를 거친다(계약 → 사이트 → 이슈).
 * 그 두 단계를 호출자가 밟게 하면 같은 질의가 화면과 어댑터에 두 벌 생긴다.
 */
public interface IssueQueryService {
    /** 이슈 목록 (D3-4) — 미배정 필터를 포함한다. */
    Page<IssueView> search(IssueQuery query, Pageable pageable);

    /** 이슈 단건 — 코멘트를 함께 싣는다. 없으면 404. */
    IssueView getIssue(long issueId);

    /** 있으면 이슈, 없으면 빈 값 — 예외로 갈래를 가르지 않는 호출자를 위한 것. */
    java.util.Optional<IssueView> findIssue(long issueId);

    /** 계약에 속한 이슈 — 사이트를 거쳐 모은다. {@code type}은 선택 필터. */
    List<IssueView> listByContract(long contractId, IssueType type, Pageable pageable);
}
