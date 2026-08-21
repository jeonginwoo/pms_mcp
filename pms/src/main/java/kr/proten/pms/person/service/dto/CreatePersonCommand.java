package kr.proten.pms.person.service.dto;

/**
 * 인력 등록 입력 (AC E2-1).
 *
 * email이 있는 이유: 로그인 ID가 email이고(§3) 신규 입사자의 사번 email은 서버가
 * 만들어 낼 수 없다 — 부록 B의 계정 규칙에서 유도 가능한 것은 초기 비밀번호뿐이다.
 * capacity·billable은 받지 않는다: 부록 B가 정한 기본값(1.0 · true)으로 시작하고,
 * 조정이 필요해지면 수정 경로(E2-2)의 몫이다.
 */
public record CreatePersonCommand(
        String name,
        Long orgUnitId,
        Long gradeId,
        Long groupId,
        String email) {
}
