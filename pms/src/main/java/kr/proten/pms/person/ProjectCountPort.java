package kr.proten.pms.person;

import java.util.Map;

/**
 * PM별 프로젝트 건수 — person이 정의하고 <b>project가 구현</b>한다
 * (2026-08-26 신설, PRD-pms §12 "노드별 프로젝트 수" 해소).
 *
 * <p><b>방향은 {@link AssignmentCountPort}와 같은 이유로 뒤집혀 있다</b>: 조직 트리
 * 화면(부록 A)이 노드마다 프로젝트 수를 요구하는데, 의존은 이미 {@code project → person}
 * 한 방향이라 person이 project를 부르면 {@code ModularityTest}가 막는 순환이 된다.
 *
 * <p><b>키가 조직 노드가 아니라 PM인 것이 이 계약의 전부다</b>. §12가 정한 정의는
 * "그 노드가 <b>PM 소속 노드</b>인, 삭제되지 않은 프로젝트 수"인데, "누가 어느 노드에
 * 속하는가"는 person이 가진 지식이다. 노드별로 접어 달라고 하면 project가 조직 트리를
 * 알게 되고 <b>같은 규칙이 두 모듈에 생긴다</b> — 소속이 바뀌는 경로(E1-1 이동·E3-5 노드
 * 이동)는 전부 person 쪽에 있으므로 접는 자리도 person이다.
 *
 * <p>별도 포트인 이유는 {@code AssignmentCountPort}가 자기 면을 "E1-2 경고에 필요한
 * 건수 하나"로 좁혀 뒀기 때문이다 — 묻는 질문이 다르면 계약을 나눈다(conventions §5 ISP).
 */
public interface ProjectCountPort {

    /**
     * PM id → 그가 대표 PM인 <b>삭제되지 않은</b> 프로젝트 수. 0건인 PM은 키가 없다.
     *
     * <p><b>완료 프로젝트를 뺴지 않는다</b>: §12의 정의가 "삭제되지 않은"이고, 이 수는
     * 화면 표시와 삭제 판정(E3-3) <b>양쪽이 같이 읽는다</b>. 여기서 상태로 거르면
     * 화면이 "프로젝트 14"라고 적어 둔 노드에서 삭제가 성공한다(2026-08-26 사용자 결정).
     *
     * <p>노드마다 묻지 않고 한 번에 묶어 받는다 — 조직 18노드·인원 44명이라 N+1이 눈에
     * 띄지는 않지만, 개수 질문을 목록마다 반복할 이유가 없다({@code PersonRepository.countByGrade}
     * 선례 · conventions §6).
     */
    Map<Long, Long> countByManager();
}
