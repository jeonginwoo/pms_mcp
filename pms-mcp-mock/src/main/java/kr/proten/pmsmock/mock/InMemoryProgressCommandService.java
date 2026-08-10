package kr.proten.pmsmock.mock;

import kr.proten.pmsmock.MockData;
import kr.proten.pmsmock.model.Person;
import kr.proten.pmsmock.model.Project;
import kr.proten.pmsmock.port.ProgressCommandService;
import kr.proten.pmsmock.port.ToolError;
import kr.proten.pmsmock.port.dto.UpdateProgressResult;

public class InMemoryProgressCommandService implements ProgressCommandService {

    private final MockData data;
    private final VisibilityPolicy visibility;

    public InMemoryProgressCommandService(MockData data, VisibilityPolicy visibility) {
        this.data = data;
        this.visibility = visibility;
    }

    @Override
    public UpdateProgressResult updateProgress(int callerId, int projectId, int percent,
                                               int version, boolean confirmed) {
        Person caller = data.person(callerId);
        Project project = data.projects.get(projectId);
        if (project == null || !visibility.canSeeProject(caller, project)) {
            throw ToolError.notFound(); // 404 은닉
        }
        // 합집합 판정(상위 PRD §4-1): 프로젝트 역할(PM/PL/참여자) OR "전 프로젝트 관리" 플래그(PM 간주)
        boolean projectRole = project.isParticipant(callerId);
        boolean manageAll = data.groupOf(caller).manageAllProjects();
        if (!projectRole && !manageAll) {
            throw ToolError.forbidden("진척률 수정은 그 프로젝트의 PM·PL·참여자만 가능합니다.");
        }
        if ("완료".equals(project.status())) {
            throw ToolError.projectCompleted();
        }
        if (percent < 0 || percent > 100) {
            throw ToolError.validation("percent는 0~100 사이여야 합니다.");
        }
        if (version != project.version()) {
            throw ToolError.staleVersion(project.progress(), project.version());
        }

        int previous = project.progress();
        boolean completable = percent == 100;
        if (!confirmed) {
            String summary = "%s 진행률을 %d%% → %d%%로 변경합니다. 실행하려면 confirmed=true로 다시 호출하세요."
                    .formatted(project.name(), previous, percent)
                    + (completable ? " (100%가 되어도 상태는 자동 전이되지 않습니다 — 완료 처리는 화면에서)" : "");
            return new UpdateProgressResult(false, projectId, project.name(), previous, percent,
                    project.version(), completable, summary);
        }
        project.applyProgress(percent);
        String summary = "%s 진행률을 %d%%에서 %d%%로 변경했습니다."
                .formatted(project.name(), previous, percent)
                + (completable ? " 상태는 진행중 그대로입니다 — 완료 처리가 가능하며, 화면에서 진행할 수 있습니다." : "");
        return new UpdateProgressResult(true, projectId, project.name(), previous, percent,
                project.version(), completable, summary);
    }
}
