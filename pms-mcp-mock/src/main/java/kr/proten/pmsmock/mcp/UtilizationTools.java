package kr.proten.pmsmock.mcp;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import kr.proten.pmsmock.port.UtilizationQueryService;
import kr.proten.pmsmock.port.dto.OverbookedEntry;
import kr.proten.pmsmock.port.dto.UtilizationEntry;

/** FR-AI-11 · FR-AI-12 — 전사/타부문 scope 부재는 M-1 실험 항목(카탈로그 공백) */
@Component
public class UtilizationTools {

    private final UtilizationQueryService utilization;
    private final CallerContext caller;

    public UtilizationTools(UtilizationQueryService utilization, CallerContext caller) {
        this.utilization = utilization;
        this.caller = caller;
    }

    @McpTool(name = "get_utilization", description = """
            월별 가동률(기본·보정)을 조회한다. 기본 가동률이 투입도·과부하 판정의 기준이고,
            보정 가동률은 직급계수를 곱한 단가 가중 보조 지표다.
            scope=ME는 본인(personId 불필요), MY_TEAM은 자기 팀, DIVISION은 자기 부문,
            PERSON은 personId로 지정한 개인. 팀·부문 집계는 billable 인원만 포함한다.
            조회 가능한 범위는 서버가 판정한다.""")
    public List<UtilizationEntry> getUtilization(
            @McpToolParam(description = "조회 월, \"yyyy-MM\" 형식 (예: 2026-08)", required = true) String month,
            @McpToolParam(description = "조회 범위: ME/MY_TEAM/DIVISION/PERSON", required = true) String scope,
            @McpToolParam(description = "scope=PERSON일 때 대상 개인 id", required = false) Integer personId) {
        return utilization.getUtilization(caller.callerId(), month, scope, personId);
    }

    @McpTool(name = "list_overbooked", description = """
            지정한 월에 과부하(기본 가동률 100% 초과)인 인원과 원인 배정 목록을 반환한다.
            범위는 조회자의 가시성으로 서버가 판정하며, billable 인원만 포함한다.""")
    public List<OverbookedEntry> listOverbooked(
            @McpToolParam(description = "조회 월, \"yyyy-MM\" 형식 (예: 2026-08)", required = true) String month) {
        return utilization.listOverbooked(caller.callerId(), month);
    }
}
