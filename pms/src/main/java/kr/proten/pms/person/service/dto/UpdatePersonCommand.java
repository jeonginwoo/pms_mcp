package kr.proten.pms.person.service.dto;

/**
 * 인력 수정 입력 (AC E2-2) — 이름·소속 조직·직급·권한 그룹을 바꾼다.
 *
 * 권한 그룹 부여가 이 경로인 이유(2026-08-09 ⑦): 그룹은 사람에게 붙는 속성이지
 * 그룹 쪽의 명부가 아니다. 그룹 화면에서 인원을 담게 하면 같은 사실이 두 곳에서
 * 수정된다.
 *
 * email은 없다 — 로그인 ID 변경은 본인의 내 계정 경로(H1-2)이고, 관리자가 남의
 * 로그인 ID를 바꾸는 행위는 AC에 없다.
 *
 * @param version 낙관적 락 — 불일치 시 `409 STALE_VERSION` (§7 동시성 규약)
 */
public record UpdatePersonCommand(
        long personId,
        String name,
        Long orgUnitId,
        Long gradeId,
        Long groupId,
        boolean billable,
        long version) {
}
