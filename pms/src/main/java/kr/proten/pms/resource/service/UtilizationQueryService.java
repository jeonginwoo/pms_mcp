package kr.proten.pms.resource.service;

import java.util.List;
import kr.proten.pms.resource.service.dto.UtilizationQuery;
import kr.proten.pms.resource.service.dto.UtilizationView;

/**
 * 가동률 조회 유스케이스 — EPIC C.
 *
 * 조회 시점 계산이다(저장하지 않는다 — 캐시 미도입 2026-08-06). 가시성 필터와
 * 404 은닉은 다른 조회와 같이 이 계층에 있다(구조 원칙 3).
 *
 * 산식 원본은 상위 `PRD.md` §3이다 — 기본 = Σ배정MM ÷ 가용, 보정 = Σ(배정MM×coeff) ÷ 가용.
 * 2026-08-10 재정의로 **곱하기**이고, 오버부킹 판정은 언제나 기본이다(C1-3).
 */
public interface UtilizationQueryService {

    /**
     * 조건에 맞는 가동률 목록 (AC C1-1·C1-3·C1-5·C1-6).
     * 집계일 때만 `billable=false` 인원이 모집단에서 빠진다 — 개인 지정은 무관하다.
     */
    List<UtilizationView> find(long callerPersonId, UtilizationQuery query);
}
