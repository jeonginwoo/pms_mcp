package kr.proten.pmsmock.port;

import kr.proten.pmsmock.port.dto.MaintenanceLogsResult;

/**
 * 실전 계약: maintenance 모듈 애플리케이션 서비스.
 * id = 계약 또는 이슈 id (projectId 단순화 불가 — OEM 직접 등록 계약 존재, 2026-08-06 확정).
 * 이슈 조회는 전사(D4-3) — 가시성 필터 없음. LLM의 id 확보 경로는 M-1 실험 항목.
 */
public interface MaintenanceQueryService {

    /** type(장애/문의/요청)은 null 허용. 최근 50건 절단. 부재 id는 404 은닉. */
    MaintenanceLogsResult listLogs(int callerId, int id, String type);
}
