package kr.proten.pms.project;

/**
 * 어느 달 한 사람의 배정 한 건 — 모듈 밖으로 나가는 배정 표현이다.
 *
 * <p>합계가 아니라 <b>행</b>인 이유: 가동률 분자는 합이지만 과부하 응답은 원인을
 * 프로젝트별로 보여 준다(PRD-host {@code get_utilization}·{@code list_overbooked}의
 * {@code Cause(projectName, mm)}). 합계만 내주면 원인을 물을 때 한 번 더 부르게 되고,
 * 그 두 번째 질의는 결국 같은 행을 다시 읽는다.
 *
 * <p>{@code projectName}을 동봉하는 것도 같은 이유다 — 호출자가 id로 프로젝트를
 * 되묻게 하면 N+1이 모듈 경계를 넘어 생긴다.
 */
public record MonthlyAssignment(long personId, long projectId, String projectName, double monthlyMm) {
}
