package kr.proten.pmsmock.mcp;

import java.util.List;
import kr.proten.pmsmock.port.ProjectDto;
import kr.proten.pmsmock.port.ProjectQueryPort;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * 프로젝트 조회 MCP 도구 — 실전 PMS의 project 모듈 internal/web으로 그대로 승격한다.
 * description은 5-2 카탈로그 문안 그대로(모델이 읽는 계약).
 */
@Component
public class ProjectMcpTools {
    // 프로젝트 검색 포트 (실전에서는 application 서비스)
    private final ProjectQueryPort projectQuery;

    public ProjectMcpTools(ProjectQueryPort projectQuery) {
        this.projectQuery = projectQuery;
    }

    @McpTool(name = "search_projects",
             description = "현재 사용자가 볼 수 있는 프로젝트를 검색한다. "
                         + "상태(계약대기/수주확정/진행중/완료/유지보수중)·키워드 필터, "
                         + "projectId 지정 시 해당 1건의 상세(일정·진행률·배정)를 반환")
    public List<ProjectDto> searchProjects(
            @McpToolParam(description = "상태 필터. 생략 시 전체", required = false) String status,
            @McpToolParam(description = "프로젝트명/고객사 키워드", required = false) String keyword,
            @McpToolParam(description = "특정 프로젝트 ID (상세 조회)", required = false) Long projectId) {
        return projectQuery.searchVisible(status, keyword, projectId);
    }
}
