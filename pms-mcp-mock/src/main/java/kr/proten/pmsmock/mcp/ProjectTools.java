package kr.proten.pmsmock.mcp;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import kr.proten.pmsmock.port.ProjectQueryService;

/**
 * FR-AI-10. description은 모델이 읽는 문서(구현_노트 §5) — B2-1 실험으로 다듬어
 * 최종 카탈로그로 승격된다(변경은 공용 결정 기록 경유).
 * 목록/상세 겸용 구조 자체가 M-1 실험 대상(`get_project` 분리 — 2026-07-31 유예).
 */
@Component
public class ProjectTools {

    private final ProjectQueryService projects;
    private final CallerContext caller;

    public ProjectTools(ProjectQueryService projects, CallerContext caller) {
        this.projects = projects;
        this.caller = caller;
    }

    @McpTool(name = "search_projects", description = """
            프로젝트를 검색하거나 상세를 조회한다. 조회 가능한 범위(가시성)는 서버가 판정한다.
            projectId를 지정하면 그 프로젝트의 상세(일정·진행률·배정 인원·version)를 반환한다 —
            진척률 수정(update_progress)에 필요한 version은 이 상세 조회로 확보한다.
            projectId가 없으면 status·keyword로 필터한 목록을 반환한다.""")
    public Object searchProjects(
            @McpToolParam(description = "프로젝트 상태 필터: 계약대기/수주확정/진행중/완료", required = false) String status,
            @McpToolParam(description = "이름·고객사·솔루션에 대한 키워드 (공백 구분 토큰 전부 포함 검색)", required = false) String keyword,
            @McpToolParam(description = "프로젝트 id — 지정 시 상세 조회", required = false) Integer projectId) {
        if (projectId != null) {
            return projects.getProjectDetail(caller.callerId(), projectId);
        }
        return projects.searchProjects(caller.callerId(), status, keyword);
    }
}
