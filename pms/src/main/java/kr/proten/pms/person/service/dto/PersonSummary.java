package kr.proten.pms.person.service.dto;

/**
 * 인력 화면이 쓰는 인원 1행 (부록 A `/people` · `/settings` 사용자 관리).
 *
 * <p>{@code PersonRef}와 표시 필드가 겹치지만 <b>따로 두는 이유</b>는 소비자가 다르기
 * 때문이다. {@code PersonRef}는 모듈 루트 계약이라 project·maintenance·`/mcp`가 함께
 * 쓰고, 거기에 화면 편집용 값을 얹으면 도구 응답에도 그대로 나간다("도구 응답은 화면
 * 응답이 아니다" — 구현_노트 §5). 그래서 루트 계약은 그대로 두고 웹 뷰만 넓혔다.
 *
 * <p>넓힌 것은 <b>수정 폼이 필요로 하는 값</b>이다(2026-08-24): §7 라우트 표에 인원
 * 상세 조회가 따로 없으므로 이 목록이 곧 사용자 관리 화면의 원천인데,
 * {@code PUT /api/people/{id}}(E2-2)는 {@code orgUnitId}·{@code gradeId}·{@code groupId}와
 * {@code version}을 요구한다. 이름만 있는 목록으로는 폼을 채울 수도, 보낼 수도 없었다.
 *
 * <p>권한 그룹은 <b>id만</b> 싣는다 — 이름은 관리 화면이 {@code GET /api/permission-groups}
 * 로 해석한다(그 라우트는 관리 플래그가 있어야 열린다). 그룹 <b>이름</b>을 인력 목록에
 * 실으면 가시성만 있으면 남의 권한 등급이 읽히므로, 화면에 필요한 최소만 준다.
 *
 * @param version 낙관적 락 (§7 — 단건 응답은 version을 포함한다)
 */
public record PersonSummary(
        Long id,
        String name,
        String orgUnit,
        String division,
        String grade,
        Long orgUnitId,
        Long gradeId,
        Long groupId,
        long version) {
}
