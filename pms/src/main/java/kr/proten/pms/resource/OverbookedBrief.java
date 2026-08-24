package kr.proten.pms.resource;

import java.util.List;

/**
 * 과부하 인원 한 명과 그 원인 — MCP `list_overbooked` 대응 (PRD-host FR-AI-12).
 *
 * <p>과부하는 <b>기본 가동률 100% 초과</b>다(2026-08-10 재정의 — 구 "보정 > 100" 대체).
 * 보정은 단가 가중 보조 지표라 판정에 쓰지 않는다.
 *
 * <p>`division`이 없는 것은 의도다: `get_utilization`은 집계 결과를 소속별로 묶으려고
 * 팀·부문을 둘 다 받지만(C1-6), 과부하 목록은 "누가·얼마나·왜"를 묻는 목록이라 부문까지
 * 필요하지 않다 — MCP `OverbookedEntry`도 같은 4필드 + 원인이다. 계약은 소비자가 실제로
 * 쓰는 면만 연다.
 *
 * @param causes 큰 것부터. 과부하의 원인을 물었을 때 첫 줄이 가장 큰 원인이어야 한다
 */
public record OverbookedBrief(
        long personId,
        String name,
        String team,
        double basicPct,
        List<Cause> causes) {

    /**
     * 원인 배정 한 줄 — 그 달 이 사람의 가동률에 프로젝트 하나가 기여한 M/M.
     *
     * <p>진행중 프로젝트의 배정만 들어온다(모집단 규칙 — 2026-08-10). 그래서 원인 M/M의
     * 합은 `assignedMm`과 같고, "왜 과부하인가"가 목록만으로 설명된다.
     */
    public record Cause(String projectName, double mm) {
    }
}
