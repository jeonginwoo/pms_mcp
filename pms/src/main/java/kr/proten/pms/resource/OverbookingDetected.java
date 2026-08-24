package kr.proten.pms.resource;

import java.time.YearMonth;

/**
 * 그 사람이 그 달에 과부하다 — resource가 발행하고 notification이 구독한다 (§8 · AC F1-1).
 *
 * <p>발행 시점이 <b>배정 변경 직후</b>인 이유: 가동률은 조회 시점 계산이라(캐시 미도입
 * 2026-08-06) resource에는 "값이 바뀌는 순간"이 없다. 유일하게 확실한 계기가 분자가
 * 바뀌는 때, 즉 배정 변경이다({@code AssignmentChanged} 구독 — ROADMAP B1-3).
 *
 * <p><b>이미 과부하이던 사람에게는 발행하지 않는다</b>: 배정을 두 번 고치면 알림이 두 번
 * 가고, 멱등 키(F1-2)는 같은 사건에 대한 중복만 막지 "여전히 과부하다"를 막지 못한다.
 * 넘어가는 순간만 사건이다.
 *
 * @param basicPct 기본 가동률 — 판정도 표시도 이 값이다(보정은 판정에 쓰지 않는다 · C1-3)
 */
public record OverbookingDetected(long personId, YearMonth month, double basicPct) {
}
