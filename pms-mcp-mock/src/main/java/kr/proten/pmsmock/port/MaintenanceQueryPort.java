package kr.proten.pmsmock.port;

import java.util.List;

/**
 * 유지보수 이력 조회 포트 — 실전 PMS의 maintenance 모듈 application 서비스가 구현할 계약.
 * 응답은 최신순 최근 50건으로 서버에서 절단한다(FR-AI-14 — 긴 이력의 토큰 폭발 방지).
 */
public interface MaintenanceQueryPort {
    /**
     * 프로젝트/유지보수 계약의 이력을 조회합니다. type은 SR·장애·정기점검·패치 중 하나이며 생략 시 전체입니다.
     */
    List<MaintenanceLogDto> findLogs(long id, String type);
}
