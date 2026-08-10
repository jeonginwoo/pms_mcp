package kr.proten.pmsmock.mock;

import java.util.List;

import kr.proten.pmsmock.MockData;
import kr.proten.pmsmock.model.Person;
import kr.proten.pmsmock.model.Project;
import kr.proten.pmsmock.port.ProjectQueryService;
import kr.proten.pmsmock.port.ToolError;
import kr.proten.pmsmock.port.dto.ProjectDetail;
import kr.proten.pmsmock.port.dto.ProjectSummary;

public class InMemoryProjectQueryService implements ProjectQueryService {

    private final MockData data;
    private final VisibilityPolicy visibility;

    public InMemoryProjectQueryService(MockData data, VisibilityPolicy visibility) {
        this.data = data;
        this.visibility = visibility;
    }

    @Override
    public List<ProjectSummary> searchProjects(int callerId, String status, String keyword) {
        Person caller = data.person(callerId);
        return data.projects.values().stream()
                .filter(p -> visibility.canSeeProject(caller, p))
                .filter(p -> status == null || status.isBlank() || p.status().equals(status))
                .filter(p -> matchesKeyword(p, keyword))
                .map(InMemoryProjectQueryService::toSummary)
                .toList();
    }

    @Override
    public ProjectDetail getProjectDetail(int callerId, int projectId) {
        Person caller = data.person(callerId);
        Project p = data.projects.get(projectId);
        if (p == null || !visibility.canSeeProject(caller, p)) {
            throw ToolError.notFound(); // 404 은닉 — 부재/범위 밖 비구분
        }
        List<String> participants = p.assigneeIds().stream()
                .map(id -> data.person(id).name())
                .toList();
        return new ProjectDetail(p.id(), p.name(), p.client(), p.status(), p.progress(),
                p.startDate(), p.endDate(), p.contractMm(), p.engagement(), p.solution(),
                data.person(p.managerId()).name(), participants, p.team(), p.division(), p.version());
    }

    private static boolean matchesKeyword(Project p, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        // 공백 구분 토큰 전부 포함 검색 — 이름·고객사·솔루션 대상
        String haystack = p.name() + " " + p.client() + " " + p.solution();
        for (String token : keyword.trim().split("\\s+")) {
            if (!haystack.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private static ProjectSummary toSummary(Project p) {
        return new ProjectSummary(p.id(), p.name(), p.client(), p.status(), p.progress(),
                p.startDate(), p.endDate(), p.team(), p.division());
    }
}
