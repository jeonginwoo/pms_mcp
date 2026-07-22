package kr.proten.pmsmock.mcp;

import java.time.YearMonth;
import java.util.List;
import kr.proten.pmsmock.port.OverbookingDto;
import kr.proten.pmsmock.port.UtilizationQueryPort;
import kr.proten.pmsmock.port.UtilizationReportDto;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * 가동률 조회 MCP 도구 — 실전 PMS의 resource 모듈 internal/web으로 그대로 승격한다.
 * description은 5-2 카탈로그 문안 그대로(모델이 읽는 계약).
 */
@Component
public class UtilizationMcpTools {
    // 가동률 조회 포트 (실전에서는 application 서비스)
    private final UtilizationQueryPort utilizationQuery;

    public UtilizationMcpTools(UtilizationQueryPort utilizationQuery) {
        this.utilizationQuery = utilizationQuery;
    }

    @McpTool(name = "get_utilization",
             description = "지정 월의 인력 가동률을 조회한다. 직급계수 보정 가동률 포함. "
                         + "scope: MY_TEAM(우리 팀) | DIVISION(본부) | PERSON(특정인)")
    public UtilizationReportDto getUtilization(
            @McpToolParam(description = "대상 월, YYYY-MM", required = true) String month,
            @McpToolParam(description = "조회 범위", required = true) String scope,
            @McpToolParam(description = "scope=PERSON일 때 대상자 ID", required = false) Long personId) {
        return utilizationQuery.report(YearMonth.parse(month), scope, personId);
    }

    @McpTool(name = "list_overbooked",
             description = "지정 월에 보정 가동률이 100%를 초과(과부하)한 인원과 원인 배정을 조회한다")
    public List<OverbookingDto> listOverbooked(
            @McpToolParam(description = "대상 월, YYYY-MM", required = true) String month) {
        return utilizationQuery.findOverbooked(YearMonth.parse(month));
    }
}
