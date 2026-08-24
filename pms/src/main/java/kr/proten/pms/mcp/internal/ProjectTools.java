package kr.proten.pms.mcp.internal;

import java.time.LocalDate;
import java.util.List;
import kr.proten.pms.mcp.internal.dto.ProjectDetail;
import kr.proten.pms.mcp.internal.dto.ProjectSummary;
import kr.proten.pms.project.ProjectBrief;
import kr.proten.pms.project.ProjectDetailBrief;
import kr.proten.pms.project.ProjectLookupService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * FR-AI-10 — project 조회 실연결(2026-08-23). 목록/상세 겸용 구조는 분리 불요로
 * 확정됐다(2026-08-12 결정 — B2-1 실측: 목록 → 상세 → version → 쓰기 2단 도약 완주).
 * 반환형이 `Object`인 것이 그 겸용의 대가다 — 갈래마다 다른 레코드를 싣는다.
 *
 * **절단 50건을 어댑터가 든다**: maintenance와 같은 판단이다. 그 숫자는 도구
 * description의 약속이고 description은 이 모듈이 소유한다 — 화면은 페이지 봉투를
 * 쓰고 챗은 절단을 쓰는데, 그 차이는 부르는 쪽 사정이다. 절단을 문구로 약속하지
 * 않으면 모델이 잘린 개수를 전체 개수로 답한다(2026-08-23 결정).
 *
 * **404 은닉은 도메인이 든다**: 프로젝트는 가시성 밖이 부재와 같은 404로 숨어야 하고
 * (AC A3-2) 그 관문은 내부 유스케이스가 갖는다. 어댑터는 예외를 문구로 옮기기만
 * 한다. maintenance가 `Optional`을 받아 어댑터에서 404를 만드는 것과 갈리는데,
 * 갈리는 이유는 유지보수엔 숨길 것이 없다는 것(전사 공개 AC D4-3)이다.
 *
 * description은 모델이 읽는 문서(구현_노트 §5)로 B2-1 실험에서 확정된 카탈로그 문구다.
 */
@Component
public class ProjectTools {

    /** description이 약속한 "최근 50건". */
    private static final int TOOL_LIMIT = 50;

    private final ProjectLookupService projects;
    private final CallerContext caller;

    public ProjectTools(ProjectLookupService projects, CallerContext caller) {
        this.projects = projects;
        this.caller = caller;
    }

    @McpTool(name = "search_projects", description = """
            프로젝트를 검색하거나 상세를 조회한다. 조회 가능한 범위(가시성)는 서버가 판정한다.
            projectId를 지정하면 그 프로젝트의 상세(일정·진행률·배정 인원·version)를 반환한다 —
            진척률 수정(update_progress)에 필요한 version은 이 상세 조회로 확보한다.
            projectId가 없으면 status·keyword로 필터한 목록을 반환한다.
            목록은 착수일 내림차순으로 최근 50건까지만 반환된다 — 결과가 잘렸을 수 있으므로
            전체 개수를 단정하지 말고, 많으면 status·keyword로 좁혀 다시 검색한다.""",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public Object searchProjects(
            @McpToolParam(description = "프로젝트 상태 필터: 계약대기/수주확정/진행중/완료/유지보수중", required = false) String status,
            @McpToolParam(description = "이름·고객사·솔루션에 대한 검색어 — 입력한 문구가 그대로 들어 있는 것을 찾는다(부분 일치)", required = false) String keyword,
            @McpToolParam(description = "프로젝트 id — 지정 시 상세 조회", required = false) Integer projectId) {
        long callerId = caller.callerId();

        if (projectId != null) {
            return toDetail(ToolCalls.translating(() -> projects.detail(callerId, projectId)));
        }

        return ToolCalls.translating(
                        () -> projects.search(callerId, status, keyword, TOOL_LIMIT)).stream()
                .map(ProjectTools::toSummary)
                .toList();
    }

    private static ProjectSummary toSummary(ProjectBrief project) {
        return new ProjectSummary(
                (int) project.id(),
                project.name(),
                project.client(),
                project.status(),
                project.progress(),
                text(project.startDate()),
                text(project.endDate()),
                project.team(),
                project.division());
    }

    private static ProjectDetail toDetail(ProjectDetailBrief project) {
        return new ProjectDetail(
                (int) project.id(),
                project.name(),
                project.client(),
                project.status(),
                project.progress(),
                text(project.startDate()),
                text(project.endDate()),
                project.contractMm(),
                project.engagement(),
                project.solution(),
                project.pm(),
                project.participants(),
                project.team(),
                project.division(),
                (int) project.version());
    }

    /** 날짜는 ISO 문자열로 — 모델이 다시 파싱하지 않게 한다. 부재는 null이다. */
    private static String text(LocalDate date) {
        return date == null ? null : date.toString();
    }
}
