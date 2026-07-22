package kr.proten.pmsmock.port;

import java.time.YearMonth;
import java.util.List;

/**
 * 가동률 조회 포트 — 실전 PMS의 resource 모듈 application 서비스가 구현할 계약.
 *
 * 보정 공식(프로토타입 frontend/src/store.jsx 정본화):
 * - mm = 해당 월 배정 MM 합, 가용 기준 1.0MM
 * - rate(무보정 %) = round(mm × 100)
 * - adjustedRate(보정 %) = round(rate × 직급계수)
 * - 과부하 ⇔ adjustedRate > 100
 */
public interface UtilizationQueryPort {
    /**
     * 지정 월의 가동률을 조회합니다. 배정이 없는 인원도 0%로 포함해 여유 인력 탐색(S-2)에 쓰입니다.
     * scope가 PERSON이면 personId가 필수입니다.
     */
    UtilizationReportDto report(YearMonth month, String scope, Long personId);

    /**
     * 지정 월에 보정 가동률이 100%를 초과한 인원을 보정 가동률 내림차순으로 조회합니다.
     */
    List<OverbookingDto> findOverbooked(YearMonth month);
}
