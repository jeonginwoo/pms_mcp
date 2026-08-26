package kr.proten.pms.person;

import java.util.List;

/**
 * 퇴사 처리에 필요한 배정 조회·해제 — person이 정의하고 <b>project가 구현</b>한다
 * (2026-08-26 신설, PRD-pms §12 ③ "인원 삭제 처리" 해소).
 *
 * <p><b>방향은 {@link AssignmentCountPort}·{@link ProjectCountPort}와 같은 이유로
 * 뒤집혀 있다</b>: 의존은 이미 {@code project → person} 한 방향이라 person이 project를
 * 부르면 {@code ModularityTest}가 막는 순환이 된다.
 *
 * <p><b>세 번째 포트를 또 나눈 이유</b>(§12 등재문은 {@code AssignmentCountPort}를
 * 목록으로 넓히라고 적었다): 그 계약의 javadoc이 자기 면을 "E1-2 경고에 필요한 건수
 * 하나"로 좁힌 근거를 이미 적어 뒀다. 넓히면 그 근거를 지워야 하고, 경고가 쓰지도 않는
 * 필드에 person이 의존하게 된다. 어제 {@code ProjectCountPort}가 같은 판단을 한 번 더
 * 했으므로 <b>질문마다 포트를 나눈다</b>가 이 모듈의 규칙이다(2026-08-26 사용자 결정).
 *
 * <p>조회와 실행이 한 계약에 같이 있는 것은 {@code AccountPort} 선례를 따른 것이고,
 * 나뉜 자리가 <b>판정은 person · 실행은 project</b>라는 점이 중요하다: "PM이면 거절"은
 * E2-3의 규칙이라 person이 {@link #findLiveAssignments}로 읽고 스스로 판정하며,
 * 감사·이벤트를 동반하는 종료는 배정을 가진 project가 든다.
 */
public interface AssignmentReleasePort {

    /**
     * 지금 물려 있는 배정 — 없으면 빈 목록이다. 정렬은 프로젝트 이름 순이다
     * (안내 문구가 실행마다 달라지지 않게).
     *
     * <p>완료·유지보수중 프로젝트의 배정은 <b>빠진다</b>({@link LiveAssignment} 참조).
     */
    List<LiveAssignment> findLiveAssignments(long personId);

    /**
     * 참여자 배정을 전부 종료한다 — 종료된 건수를 돌려준다.
     *
     * <p><b>PM 배정은 건드리지 않는다</b>. 호출 전에 person이 이미 거절하므로 여기까지
     * PM이 남아 오지 않지만, 그래도 거르는 것은 이 계약 하나만 보고도 "PM은 교체 없이
     * 사라지지 않는다"(A6-5 불변식)가 성립하게 하기 위해서다 — B2-1의
     * {@code close}가 PM 배정을 거절하는 것과 같은 규칙이다.
     *
     * <p>종료는 B2-1의 규칙을 그대로 쓴다: 상태를 CLOSED로 바꾸고 {@code endDate}를
     * 종료월 말일로 당긴다(그보다 이른 종료일은 늘리지 않는다). 감사 기록과
     * {@code AssignmentChanged} 발행도 그 경로와 같다 — 퇴사로 끊긴 배정이 이력에서
     * 손으로 끊은 것과 달라 보일 이유가 없다.
     */
    int closeParticipantAssignments(long callerPersonId, long personId);
}
