package kr.proten.pms.resource.service.impl;

import java.util.List;
import kr.proten.pms.common.exception.NotImplementedException;
import kr.proten.pms.resource.service.UtilizationQueryService;
import kr.proten.pms.resource.service.dto.UtilizationQuery;
import kr.proten.pms.resource.service.dto.UtilizationView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가동률 조회 — **골격만 서 있고 산식은 아직 없다** (2026-08-22).
 *
 * 이미 정해져 있는 것(여기 주석이 그 기록이다):
 * - 모집단: 가시성 범위 안에서, 집계일 때만 `billable=false` 제외 (C1-5)
 * - 분모: 그 달 `Capacity` 행이 있으면 그 값, 없으면 `Person.capacity` 기본값
 * - 분자: 그 달과 겹치는 `ACTIVE` 배정의 `monthlyMm` 합
 * - 과부하: 기본 > 100 (보정이 아니다 — C1-3)
 *
 * 협력자를 미리 주입하지 않는다: 던지기만 하는 본문에 붙여 두면 모듈 의존 그래프에
 * 없는 배선이 기록된다. 구현이 들어올 때 `CapacityRepository`·가시성 계약과 함께 받는다.
 *
 * TODO(C1-1): 분자를 얻으려면 project 모듈이 "인원×월 배정 M/M"을 내주는 계약이
 *   필요하다. 지금 project가 공개한 것은 `ProjectQueryService`(목록·단건)와
 *   `AssignmentService`(쓰기)뿐이라 조회 경로가 없다. 배정 엔티티를 직접 읽는 것은
 *   모듈 경계 위반이므로(ModularityTest) **project 서비스 계약 추가**로 풀어야 하고,
 *   그것은 애플리케이션 서비스 API 변경이라 공용 결정 기록을 거친다.
 * TODO(C1-4): 배정 변경 2초 내 반영 — 매 조회 계산이라 이벤트 재계산은 필요 없지만,
 *   그 사실을 증명하는 통합 테스트가 아직 없다.
 */
@Service
@Transactional(readOnly = true)
class UtilizationQueryServiceImpl implements UtilizationQueryService {
    @Override
    public List<UtilizationView> find(long callerPersonId, UtilizationQuery query) {
        throw new NotImplementedException("가동률 조회 (C1-1·C1-3·C1-5·C1-6)");
    }
}
