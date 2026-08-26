package kr.proten.pms.person;

/**
 * 한 사람이 <b>지금 물려 있는</b> 배정 한 줄 — {@link AssignmentReleasePort}의 반환형.
 *
 * <p>"물려 있다"는 배정이 살아 있고({@code ACTIVE}) <b>프로젝트도 아직 진행 중</b>이라는
 * 뜻이다({@code ProjectStatus.isLive()} — 완료·유지보수중 제외). 배정 상태만 보면 완료된
 * 프로젝트의 배정이 그대로 걸린다(2026-08-26 실측: ACTIVE 462건 중 384건이 완료 건).
 *
 * <p>면이 세 칸인 것은 의도다 — 이 계약을 쓰는 유스케이스(퇴사 처리 §12 ③)가 답해야
 * 하는 것이 "몇 건인가 · 어느 프로젝트인가 · PM인가" 셋뿐이다. 기간·M/M·역할 열거까지
 * 실으면 person이 배정의 모양에 의존하게 된다(conventions §5 ISP —
 * {@code AssignmentCountPort}가 자기 면을 건수 하나로 좁힌 것과 같은 규율).
 *
 * @param manager PM 배정인가 — 참여자 배정만 자동 종료하고 PM은 교체를 요구한다(§12 ③)
 */
public record LiveAssignment(long projectId, String projectName, boolean manager) {
}
