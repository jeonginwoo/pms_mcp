package kr.proten.pms.mcp.internal;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import kr.proten.pms.mcp.internal.dto.OverbookedEntry;
import kr.proten.pms.mcp.internal.dto.UtilizationEntry;
import kr.proten.pms.resource.OverbookedBrief;
import kr.proten.pms.resource.UtilizationBrief;
import kr.proten.pms.resource.UtilizationLookupService;
import kr.proten.pms.resource.UtilizationScope;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * FR-AI-11 · FR-AI-12 — resource 실연결(2026-08-24). COMPANY scope는 2026-08-11 결정 ③
 * (카탈로그 공백 해소). 이 배선으로 도구 8종이 전부 실연결됐다.
 *
 * **범위 판정을 어댑터가 갖지 않는다**: `scope` 낱말을 조직 id로 바꿔 넘기는 우회가
 * 가능했지만, 그러면 "MY_TEAM이 누구인가"의 답이 어댑터에 남아 웹(`?orgUnitId=`)과
 * 챗이 갈릴 수 있다. `UtilizationScope`를 resource가 갖는 이유가 그것이고(2026-08-24
 * 결정 ②), 어댑터는 문자열을 그 낱말로 옮기기만 한다 — 모르는 낱말의 거절도 도메인
 * 몫이다(`UtilizationScope.from`이 422를 던진다).
 *
 * **월 파싱만 어댑터가 든다**: `"yyyy-MM"`은 표현이고 계약은 `YearMonth`를 주고받는다
 * (`UtilizationBrief` javadoc). 도메인 예외가 아니라 **파라미터 파싱 실패**이므로
 * `ToolCalls`를 지나지 않지만, 문구는 다른 422와 같은 `ToolError`에서 나온다 —
 * conventions §4가 막는 것은 도구마다 제각기 문구를 짓는 일이다.
 *
 * **집계 평균은 싣지 않는다**: 도구는 인원별 행을 주고 부문별 정리는 모델이 한다
 * (2026-08-11 결정 ③이 응답에 team·division을 실은 이유 · eval A-01).
 *
 * description은 모델이 읽는 문서(구현_노트 §5)로 B2-1 실험에서 확정된 카탈로그 문구다.
 */
@Component
public class UtilizationTools {

    private final UtilizationLookupService utilization;
    private final CallerContext caller;

    public UtilizationTools(UtilizationLookupService utilization, CallerContext caller) {
        this.utilization = utilization;
        this.caller = caller;
    }

    @McpTool(name = "get_utilization", description = """
            월별 가동률(기본·보정)을 조회한다. 기본 가동률이 투입도·과부하 판정의 기준이고,
            보정 가동률은 직급계수를 곱한 단가 가중 보조 지표다 — 과부하·투입 판정에는 쓰지 않는다.
            scope=ME는 본인(personId 불필요), MY_TEAM은 자기 팀, DIVISION은 자기 부문,
            COMPANY는 전사, PERSON은 personId로 지정한 개인.
            팀·부문·전사 집계는 billable 인원만 포함하며, 각 항목에 팀·부문이 함께 반환된다.
            조회 가능한 범위는 서버가 판정한다.""",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public List<UtilizationEntry> getUtilization(
            @McpToolParam(description = "조회 월, \"yyyy-MM\" 형식 (예: 2026-08)", required = true) String month,
            @McpToolParam(description = "조회 범위: ME/MY_TEAM/DIVISION/COMPANY/PERSON", required = true) String scope,
            @McpToolParam(description = "scope=PERSON일 때 대상 개인 id", required = false) Integer personId) {
        long callerId = caller.callerId();
        YearMonth targetMonth = monthOf(month);

        return ToolCalls.translating(() -> utilization.find(
                        callerId, targetMonth, UtilizationScope.from(scope), longOrNull(personId)))
                .stream()
                .map(UtilizationTools::toEntry)
                .toList();
    }

    @McpTool(name = "list_overbooked", description = """
            지정한 월에 과부하(기본 가동률 100% 초과)인 인원과 원인 배정 목록을 반환한다.
            범위는 조회자의 가시성으로 서버가 판정하며, billable 인원만 포함한다.""",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public List<OverbookedEntry> listOverbooked(
            @McpToolParam(description = "조회 월, \"yyyy-MM\" 형식 (예: 2026-08)", required = true) String month) {
        long callerId = caller.callerId();
        YearMonth targetMonth = monthOf(month);

        return ToolCalls.translating(() -> utilization.findOverbooked(callerId, targetMonth))
                .stream()
                .map(UtilizationTools::toOverbooked)
                .toList();
    }

    /**
     * `"yyyy-MM"` → {@link YearMonth}. 실패는 422다 — 모델이 무엇을 고쳐 다시 부를지
     * 알아야 하므로 형식을 문구에 그대로 싣는다(FR-AI-26의 "재시도 판단").
     */
    private static YearMonth monthOf(String month) {
        if (month == null || month.isBlank()) {
            throw ToolError.validation("조회 월은 필수입니다. \"yyyy-MM\" 형식으로 지정해야 합니다 (예: 2026-08).");
        }

        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw ToolError.validation(
                    "조회 월 형식이 올바르지 않습니다: \"" + month + "\". \"yyyy-MM\" 형식이어야 합니다 (예: 2026-08).");
        }
    }

    private static UtilizationEntry toEntry(UtilizationBrief row) {
        return new UtilizationEntry(
                (int) row.personId(),
                row.name(),
                row.team(),
                row.division(),
                row.month().toString(),
                row.assignedMm(),
                row.availableMm(),
                row.basicPct(),
                row.adjustedPct());
    }

    private static OverbookedEntry toOverbooked(OverbookedBrief row) {
        return new OverbookedEntry(
                (int) row.personId(),
                row.name(),
                row.team(),
                row.basicPct(),
                row.causes().stream()
                        .map(cause -> new OverbookedEntry.Cause(cause.projectName(), cause.mm()))
                        .toList());
    }

    private static Long longOrNull(Integer value) {
        return value == null ? null : value.longValue();
    }
}
