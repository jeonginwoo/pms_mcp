package kr.proten.pms.mcp.internal;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * FR-AI-10. 목록/상세 겸용 구조는 분리 불요로 확정됐다(2026-08-12 결정 — B2-1 근거).
 *
 * TODO(M1 project): project 모듈이 조회 계약을 모듈 루트로 승격하면 이 던지는 자리가
 * 사라진다. 승격 전까지는 도구를 노출한 채 FR-AI-26 표준 오류로 실패 사실을 알린다 —
 * 카탈로그에서 빼면 모델이 "그 기능이 없다"고 단정해 다른 도구로 우회한다.
 */
@Component
public class ProjectTools {

    @McpTool(name = "search_projects", description = """
            프로젝트를 검색하거나 상세를 조회한다. 조회 가능한 범위(가시성)는 서버가 판정한다.
            projectId를 지정하면 그 프로젝트의 상세(일정·진행률·배정 인원·version)를 반환한다 —
            진척률 수정(update_progress)에 필요한 version은 이 상세 조회로 확보한다.
            projectId가 없으면 status·keyword로 필터한 목록을 반환한다.""")
    public Object searchProjects(
            @McpToolParam(description = "프로젝트 상태 필터: 계약대기/수주확정/진행중/완료", required = false) String status,
            @McpToolParam(description = "이름·고객사·솔루션에 대한 키워드 (공백 구분 토큰 전부 포함 검색)", required = false) String keyword,
            @McpToolParam(description = "프로젝트 id — 지정 시 상세 조회", required = false) Integer projectId) {
        throw ToolError.unavailable("프로젝트 조회");
    }
}
