package kr.proten.pms.maintenance;

import java.util.List;

/**
 * 계약 하나에 딸린 이슈 묶음 — MCP {@code MaintenanceLogsResult}가 채워지는 모양이다.
 *
 * @param matched 무엇으로 찾았는지({@code 계약} 또는 {@code 이슈}) — 도구가 계약 id와
 *                이슈 id를 같은 파라미터로 받으므로 어느 쪽으로 해석됐는지 알려 준다
 * @param contractId 이슈가 계약에 붙지 않은 경우(시드 7건) null이다
 */
public record ContractIssues(
        String matched, Long contractId, String contractName, List<IssueBrief> issues) {
}
