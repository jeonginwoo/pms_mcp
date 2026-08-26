package kr.proten.pms.person.service.dto;

/**
 * 조직 노드 표현 (AC E3-1~E3-3 화면).
 *
 * @param memberCount  활성 소속 인원 수
 * @param childCount   하위 노드 수
 * @param projectCount 그 노드가 <b>PM 소속 노드</b>인, 삭제되지 않은 프로젝트 수
 *                     (부록 A 조직 트리 · PRD-pms §12 해소 2026-08-26). <b>직속 기준이고
 *                     subtree 합계가 아니다</b> — 인원·하위 노드 수와 읽는 방향이 같다.
 *                     완료 프로젝트도 센다: 이 수는 아래 {@code deletable}이 함께 읽으므로
 *                     여기서 상태로 거르면 화면이 보여 준 수와 서버가 막는 수가 갈린다
 * @param deletable    빈 노드만 삭제 가능하다는 규칙(E3-3)의 판정 결과 — 화면이 같은
 *                     규칙을 다시 구현하지 않도록 서버가 답한다. 최종 판정은 삭제 요청 시점이다
 */
public record OrgUnitView(
        Long id,
        Long parentId,
        String name,
        long memberCount,
        long childCount,
        long projectCount,
        boolean deletable) {
}
