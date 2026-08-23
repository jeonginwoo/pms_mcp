package kr.proten.pms.mcp.internal;

import java.util.List;
import kr.proten.pms.mcp.internal.dto.OverbookedEntry;
import kr.proten.pms.mcp.internal.dto.UtilizationEntry;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * FR-AI-11 · FR-AI-12 — COMPANY scope는 2026-08-11 결정 ③(카탈로그 공백 해소).
 *
 * TODO(M1 EPIC C): resource 모듈의 가동률 실구현(C1-1~C1-6) + 조회 계약 루트 승격이
 * 선행이다. 분자(Σ 인원×월 배정 M/M)를 얻을 경로가 project 쪽에 아직 없다.
 */
@Component
public class UtilizationTools {

    @McpTool(name = "get_utilization", description = """
            월별 가동률(기본·보정)을 조회한다. 기본 가동률이 투입도·과부하 판정의 기준이고,
            보정 가동률은 직급계수를 곱한 단가 가중 보조 지표다 — 과부하·투입 판정에는 쓰지 않는다.
            scope=ME는 본인(personId 불필요), MY_TEAM은 자기 팀, DIVISION은 자기 부문,
            COMPANY는 전사, PERSON은 personId로 지정한 개인.
            팀·부문·전사 집계는 billable 인원만 포함하며, 각 항목에 팀·부문이 함께 반환된다.
            조회 가능한 범위는 서버가 판정한다.""")
    public List<UtilizationEntry> getUtilization(
            @McpToolParam(description = "조회 월, \"yyyy-MM\" 형식 (예: 2026-08)", required = true) String month,
            @McpToolParam(description = "조회 범위: ME/MY_TEAM/DIVISION/COMPANY/PERSON", required = true) String scope,
            @McpToolParam(description = "scope=PERSON일 때 대상 개인 id", required = false) Integer personId) {
        throw ToolError.unavailable("가동률 조회");
    }

    @McpTool(name = "list_overbooked", description = """
            지정한 월에 과부하(기본 가동률 100% 초과)인 인원과 원인 배정 목록을 반환한다.
            범위는 조회자의 가시성으로 서버가 판정하며, billable 인원만 포함한다.""")
    public List<OverbookedEntry> listOverbooked(
            @McpToolParam(description = "조회 월, \"yyyy-MM\" 형식 (예: 2026-08)", required = true) String month) {
        throw ToolError.unavailable("가동률 조회");
    }
}
