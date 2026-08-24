package kr.proten.pms.person;

/**
 * 한 사람의 진행 중 배정이 몇 건인가 — person이 정의하고 <b>project가 구현</b>한다
 * (2026-08-24 신설, 공용 결정 기록).
 *
 * <p><b>방향을 뒤집은 이유는 순환이다</b>: 소속 이동(AC E1-2)은 "진행 중 배정이 있어도
 * 허용하되 경고"인데, 그 경고를 만들려면 person이 배정 건수를 알아야 한다. 그런데
 * 의존은 이미 {@code project → person} 한 방향이고(project가 배정에 인원 이름을 붙인다),
 * person이 project를 부르면 {@code ModularityTest}가 막는 순환이 된다. 그래서
 * {@code AccountPort}(person 정의 · auth 구현) 선례대로 <b>필요한 쪽이 계약을 정의하고
 * 가진 쪽이 구현</b>한다 — person은 project를 import하지 않는다.
 *
 * <p>면을 <b>건수 하나</b>로 좁힌 것도 의도다. 경고 문구를 만드는 데 필요한 것이 그것뿐이고,
 * 배정 목록을 받으면 person이 배정의 모양(역할·기간·M/M)에까지 의존하게 된다 —
 * {@code AssignmentDirectoryService}가 resource에 여는 면과 소비자가 다르므로 계약을
 * 나눈다(conventions §5 ISP).
 */
public interface AssignmentCountPort {

    /**
     * 진행 중 배정 건수 — 없으면 0이다.
     *
     * <p>"진행 중"은 <b>배정 자체가 살아 있는가</b>(종료되지 않았는가)이지 프로젝트 상태가
     * 아니다. 이동 경고가 답해야 하는 질문이 "이 사람이 지금 어디에 물려 있는가"이기
     * 때문이다 — 완료된 프로젝트의 배정은 이미 종료 처리된다(AC B2-1).
     */
    long countActiveAssignments(long personId);
}
