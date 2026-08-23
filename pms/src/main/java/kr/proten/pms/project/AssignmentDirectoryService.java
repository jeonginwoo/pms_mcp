package kr.proten.pms.project;

import java.time.YearMonth;
import java.util.Collection;
import java.util.List;

/**
 * 배정 조회 — 다른 모듈에 여는 project의 공개 계약 (2026-08-23 신설, 공용 결정 기록).
 *
 * <p>가동률(EPIC C)의 분자를 얻을 경로가 없어서 만든 계약이다. resource가
 * {@code ProjectAssignment}를 직접 읽는 것은 모듈 경계 위반이고({@code ModularityTest}),
 * 그렇다고 {@code ProjectQueryService}(목록·단건)나 {@code AssignmentService}(쓰기)를
 * 통째로 루트에 올리면 밖으로 나갈 이유가 없는 것까지 공개된다. 그래서
 * {@code PersonDirectoryService} 선례대로 <b>소비자가 실제로 쓰는 좁은 면</b>만 연다.
 *
 * <p><b>인원 집합을 받는다</b>: 조직 subtree·가시성·billable 판정은 person과 resource의
 * 몫이고 project는 조직을 알지 못한다(모듈 간 연결은 id로만 — PRD-pms §0). 호출자가
 * "누구를 셀지" 먼저 정하고 그 명단을 넘긴다.
 */
public interface AssignmentDirectoryService {
    /**
     * 그 달과 하루라도 겹치는 배정을 인원별로 돌려준다 — 합산은 호출자가 한다.
     *
     * <p>종료된 배정도 <b>종료월까지는</b> 포함된다: 종료 시 {@code endDate}가 종료월
     * 말일로 당겨지므로(AC B2-1) 겹침 판정 하나로 "종료월 이후 제외"가 성립한다.
     * 지난달 가동률을 오늘 조회해도 그때의 수치가 그대로 나온다.
     *
     * <p>프로젝트 상태로 거르지 않는다 — 모집단 판정(진행중만)은 호출자의 몫이라
     * 각 행에 {@link MonthlyAssignment#projectStatus}를 실어 보낸다.
     *
     * @param personIds 빈 집합이면 빈 목록 — 질의하지 않는다
     */
    List<MonthlyAssignment> findInMonth(YearMonth month, Collection<Long> personIds);
}
