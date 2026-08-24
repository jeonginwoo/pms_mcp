package kr.proten.pms.resource;

import java.time.YearMonth;

/**
 * 한 사람의 한 달 가동률 — 모듈 밖(현재는 `/mcp` 어댑터)에 내보내는 표현
 * (MCP `UtilizationEntry` 9필드 대응 · AC C1-1·C1-6).
 *
 * <p>웹의 `UtilizationView`를 그대로 올리지 않는 이유는 `ProjectLookupService` 선례와
 * 같다: 내부 dto는 웹 응답의 필드 이름을 따르고(`basic`·`adjusted`), 어댑터가 약속한
 * 이름은 `basicPct`·`adjustedPct`다. 같은 수치라도 <b>계약의 이름은 계약이 갖는다</b> —
 * 내부 dto 이름을 바꾸면 프론트가 따라 바뀌고, 그 반대도 마찬가지다.
 *
 * <p>`month`는 `YearMonth`로 낸다. `"yyyy-MM"` 문자열로 만드는 것은 표현이고,
 * 그것은 어댑터의 몫이다.
 *
 * @param basicPct    기본 = Σ배정MM ÷ 가용 × 100 — 과부하·투입 판정의 정본(C1-3)
 * @param adjustedPct 보정 = Σ(배정MM × 직급계수) ÷ 가용 × 100 — 단가 가중 보조 지표
 */
public record UtilizationBrief(
        long personId,
        String name,
        String team,
        String division,
        YearMonth month,
        double assignedMm,
        double availableMm,
        double basicPct,
        double adjustedPct) {
}
