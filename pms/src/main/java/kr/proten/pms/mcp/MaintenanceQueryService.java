package kr.proten.pms.mcp;

import java.util.List;


/**
 * 실전 계약: maintenance 모듈 애플리케이션 서비스.
 * id = 계약 또는 이슈 id (projectId 단순화 불가 — OEM 직접 등록 계약 존재, 2026-08-06 확정).
 * 조회는 전사(D4-3) — 가시성 필터·404 은닉 없음(부재 id 단건 조회 제외).
 * id 확보 경로 = searchContracts (2026-08-11 결정 ④ — 카탈로그 7종→8종).
 */
public interface MaintenanceQueryService {

    /** type(장애/문의/요청)은 null 허용. 최근 50건 절단. 부재 id는 404 은닉. */
    MaintenanceLogsResult listLogs(int callerId, int id, String type);

    /**
     * 계약 검색 — keyword는 계약명·계약사·사이트명(고객사) 부분 일치 (D4-1 웹 keyword와 동일 매칭 범위),
     * status는 예정/신규/유지/종료 (그 외 422). 둘 다 null 허용. 종료일 내림차순, 50건 절단. 빈 결과는 [].
     */
    List<ContractSummary> searchContracts(int callerId, String keyword, String status);
}
