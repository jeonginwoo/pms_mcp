package kr.proten.pmsmock.model;

/**
 * 유지보수 계약 (2026-08-06 재설계 — 계약/사이트/이슈 3층).
 * 목업 단순화: 사이트 1개를 계약에 평탄화(siteName·engineerId) — 도구가 사이트를 노출하지 않으므로 실험에 충분.
 * sourceProjectId=null 이면 OEM 직접 등록 계약(프로젝트 없음 — projectId 단순화 불가의 근거 케이스).
 */
public record MaintenanceContract(
        int id,
        String name,
        String client,
        Integer sourceProjectId,
        String startDate,
        String endDate,
        String siteName,
        Integer engineerId) {
}
