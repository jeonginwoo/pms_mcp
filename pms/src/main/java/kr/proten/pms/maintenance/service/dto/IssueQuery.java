package kr.proten.pms.maintenance.service.dto;

import kr.proten.pms.maintenance.service.entity.IssueStatus;
import kr.proten.pms.maintenance.service.entity.IssueType;

/**
 * 이슈 목록 필터 (AC D3-4) — 전부 선택.
 *
 * @param unassignedOnly 미배정 이슈만. {@code assigneeId=null}로는 표현할 수 없다 —
 *                       그 null은 이미 "담당자로 거르지 않는다"를 뜻하기 때문이다
 * @param contractId 계약으로 거르면 그 계약의 사이트들에 속한 이슈만
 */
public record IssueQuery(
        IssueStatus status,
        IssueType type,
        Long siteId,
        Long assigneeId,
        boolean unassignedOnly,
        Long contractId) {

    public static IssueQuery all() {
        return new IssueQuery(null, null, null, null, false, null);
    }
}
