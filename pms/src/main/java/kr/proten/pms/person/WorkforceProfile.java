package kr.proten.pms.person;

/**
 * 가동률 계산에 필요한 인원 속성 — 모듈 밖으로 나가는 두 번째 인원 표현이다.
 *
 * <p>{@link PersonRef}(id·이름·조직명·직급명)와 나누는 이유: 그것은 project가 배정
 * 화면을 그리려고 쓰는 표시용 참조다. 거기에 capacity·billable·계수를 얹으면 resource의
 * 관심사가 project의 컴파일 면에까지 얹힌다 — 소비자가 다르면 계약을 나눈다
 * (conventions §5).
 *
 * <p>{@code team}과 {@code division}을 <b>따로</b> 싣는다: 응답을 소속별로 묶으려면
 * 둘이 각각 필요하고(AC C1-6 · MCP {@code get_utilization}과 같은 계약), 조직 경로를
 * 문자열 하나로 주면 호출자가 그것을 다시 쪼개게 된다.
 *
 * @param defaultCapacity 가용 M/M 기본값 — 그 달 {@code Capacity} 행이 있으면 그쪽이 이긴다
 * @param billable 집계 모집단 여부 (AC C1-5) — 개인 단건 조회는 이 값과 무관하다
 * @param gradeCoeff 보정 가동률의 단가 계수 (상위 PRD §3) — 과부하 판정에는 쓰지 않는다
 */
public record WorkforceProfile(
        long personId,
        String name,
        String team,
        String division,
        double defaultCapacity,
        boolean billable,
        double gradeCoeff) {
}
