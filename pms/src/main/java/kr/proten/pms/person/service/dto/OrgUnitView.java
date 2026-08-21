package kr.proten.pms.person.service.dto;

/**
 * 조직 노드 표현 (AC E3-1~E3-3 화면).
 *
 * @param memberCount 활성 소속 인원 수
 * @param childCount  하위 노드 수
 * @param deletable   빈 노드만 삭제 가능하다는 규칙(E3-3)의 판정 결과 — 화면이 같은
 *                    규칙을 다시 구현하지 않도록 서버가 답한다. 최종 판정은 삭제 요청 시점이다
 */
public record OrgUnitView(
        Long id,
        Long parentId,
        String name,
        long memberCount,
        long childCount,
        boolean deletable) {
}
