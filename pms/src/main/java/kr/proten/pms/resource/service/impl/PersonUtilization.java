package kr.proten.pms.resource.service.impl;

import java.time.YearMonth;
import java.util.List;
import kr.proten.pms.person.WorkforceProfile;

/**
 * 한 사람의 한 달 가동률 — {@link UtilizationCalculator}가 내는 내부 표현이다.
 *
 * <p>모듈 밖으로 나가지 않는다: 웹은 {@code UtilizationView}로, {@code /mcp} 어댑터는
 * 루트의 {@code UtilizationBrief}·{@code OverbookedBrief}로 각자 필요한 만큼만 가져간다.
 * 그 둘의 <b>공통 상위</b>를 계약으로 두지 않는 이유는 담는 것이 서로 다르기 때문이다 —
 * 웹은 {@link #shares}를 버리고 MCP는 그것을 원인 목록으로 싣는다.
 *
 * <p><b>{@code shares}를 여기서 들고 있는 것이 이 record의 존재 이유</b>다. 합계만
 * 넘기면 {@code list_overbooked}의 원인({@code Cause(projectName, mm)})을 만들 때 배정을
 * 다시 조회하게 되고, 그 두 번째 조회는 "진행중 배정만"이라는 모집단 규칙을 한 벌 더
 * 갖는다(2026-08-24 결정 기록 — 지난 세션이 시드 테스트에서 걷어낸 것과 같은 형태다).
 */
record PersonUtilization(
        WorkforceProfile profile,
        YearMonth month,
        double assignedMm,
        double availableMm,
        double basicPct,
        double adjustedPct,
        List<ProjectShare> shares) {

    /**
     * 과부하 여부 — 판정은 언제나 기본 가동률이다 (AC C1-3, 2026-08-10 재정의).
     *
     * <p>이 판정이 여기 하나뿐인 것이 중요하다: 웹의 {@code ?overbooked=true} 필터와
     * MCP {@code list_overbooked}가 같은 문장을 읽어야 두 경로의 과부하 명단이 같다.
     */
    boolean overbooked() {
        return basicPct > 100.0;
    }

    /** 가동률에 기여한 프로젝트 한 건 — 과부하의 "원인" 한 줄이다. */
    record ProjectShare(String projectName, double mm) {
    }
}
