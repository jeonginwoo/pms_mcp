package kr.proten.pms.person;

/**
 * 인원 참조 — 다른 모듈이 id로 사람을 가리킬 때 쓰는 표시용 값.
 *
 * <p>{@code orgUnit}은 소속 노드의 이름(= 팀), {@code division}은 그 경로상 최상위
 * 부문(root 직계 자식)이다 — 가시성 DIVISION scope와 <b>같은 해석</b>을 쓴다
 * ({@code OrgTree.topDivisionIdOf}). 같은 트리를 두 규칙으로 읽으면 "내 부문"이
 * 화면과 집계에서 갈라진다. 부문 직속 인원은 둘이 같은 이름이 되는데, 그 사람에게는
 * 그것이 사실이다.
 *
 * <p>{@code division}은 2026-08-23에 추가했다: 프로젝트 조회 응답이 팀·부문을 실어야
 * 하고(MCP {@code ProjectSummary}·{@code ProjectDetail}) 그 값은 <b>PM의 소속</b>에서
 * 나온다(2026-08-23 결정 — 시드의 프로젝트 team·division은 구 익명 명부 PM 소속
 * 파생값이었고 382/382가 일치했다. 프로젝트는 자기 소속을 갖지 않는다 — PRD §4).
 *
 * <p>{@code active}는 <b>표시 이름을 재직 여부와 무관하게 해석하기 때문에</b> 필요하다
 * (2026-08-24 신설 — {@link PersonDirectoryService#findRefs} 규약 변경과 한 쌍이다).
 * 퇴사자가 남긴 배정·감사 행은 그대로 남으므로 이름도 남아야 하고, 화면은 그 사람이
 * 이미 없다는 사실을 이 플래그로 표시한다. 재직 여부가 <b>판정 근거</b>인 자리는
 * {@link PersonDirectoryService#existsActive}가 따로 답한다.
 */
public record PersonRef(
        Long id, String name, String orgUnit, String division, String grade, boolean active) {
}
