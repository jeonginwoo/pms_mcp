package kr.proten.pmsmock.model;

/**
 * 유지보수 계약 (2026-08-06 재설계 — 계약/사이트/이슈 3층). 상태 열거 = {예정,신규,유지,종료} (PRD-pms §4).
 * 목업 단순화: 사이트 1개를 계약에 평탄화(siteName·engineerId) — search_maintenance의 사이트명 매칭 실험에는
 * "계약명·계약사에 없는 문자열이 사이트명에만 있는" 케이스 1건이면 충분(903 가온아이).
 * sourceProjectId=null 이면 OEM 직접 등록 계약(프로젝트 없음 — projectId 단순화 불가의 근거 케이스).
 */
public record MaintenanceContract(
        int id,
        String name,
        String client,
        Integer sourceProjectId,
        String status,
        String startDate,
        String endDate,
        String siteName,
        Integer engineerId) {
}
