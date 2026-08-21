package kr.proten.pms.project.service.impl;

import java.util.Map;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.ProgressUpdateService;
import kr.proten.pms.project.service.dto.ProgressUpdateResult;
import kr.proten.pms.project.service.dto.UpdateProgressCommand;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAction;
import kr.proten.pms.project.service.entity.ProjectStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진척률 갱신 유스케이스 — AC A2-1~A2-8.
 *
 * 2단계 확인이 이 서비스의 프로토콜이다: confirmed=false는 변경 요약만 돌려주고
 * DB를 건드리지 않으며, confirmed=true에서 낙관적 락을 검사하고 커밋한다.
 * 웹 UI와 MCP `update_progress`가 같은 서비스를 쓰므로(PRD-pms US-A2) 프로토콜을
 * 여기 한 곳에 둔다 — 입구마다 다른 확인 절차를 만들면 권한·감사가 갈라진다.
 */
@Service
@Transactional
public class ProgressUpdateServiceImpl implements ProgressUpdateService {
    private final ProjectRepository projectRepository;
    private final ProjectVisibilityService projectVisibilityService;
    private final ProjectActionPermission projectActionPermission;
    private final ProjectAuditRecorder projectAuditRecorder;

    public ProgressUpdateServiceImpl(
            ProjectRepository projectRepository,
            ProjectVisibilityService projectVisibilityService,
            ProjectActionPermission projectActionPermission,
            ProjectAuditRecorder projectAuditRecorder) {
        this.projectRepository = projectRepository;
        this.projectVisibilityService = projectVisibilityService;
        this.projectActionPermission = projectActionPermission;
        this.projectAuditRecorder = projectAuditRecorder;
    }

    /**
     * 진척률을 확인 후 갱신한다.
     * 검사 순서는 형식(400) → 가시성(404 은닉) → 권한(403) → 상태(409)다 —
     * 가시성 밖 프로젝트에 대해 권한이나 상태를 알려 주면 존재가 드러난다.
     */
    public ProgressUpdateResult update(long callerPersonId, UpdateProgressCommand command) {
        requireProgressInRange(command.progress());

        Project project =
                projectVisibilityService.requireVisible(callerPersonId, command.projectId());
        // 배정 인원이면 역할 무관하게 가능하다 (상위 PRD §4-2 기본 매트릭스) —
        // 진척률은 Project.progress 단일 값이라 "본인 몫" 부분 수정 개념이 없다
        projectActionPermission.require(callerPersonId, project.getId(),
                ProjectAction.PROGRESS);
        requireInProgress(project);

        if (!command.confirmed()) {
            return ProgressUpdateResult.summary(project, command.progress());
        }

        project.requireVersion(command.version());

        Map<String, Object> before = projectAuditRecorder.snapshot(project);
        project.updateProgress(command.progress());
        Project saved = projectRepository.saveAndFlush(project);
        // 상태는 바뀌지 않으므로 UPDATE로 남는다 (AC A2-2 · §5 자동 전이 폐지)
        projectAuditRecorder.changed(callerPersonId, saved, before);

        return ProgressUpdateResult.committedOf(saved);
    }

    private void requireProgressInRange(int progress) {
        if (progress < 0 || progress > 100) {
            throw new ValidationException("진척률은 0에서 100 사이여야 합니다", "progress");
        }
    }

    /**
     * 진척률은 **진행중 상태에서만** 수정한다 (2026-08-22 결정).
     *
     * 완료는 기존 코드(A2-8 `PROJECT_COMPLETED`)를 그대로 쓴다 — 재개하면 다시 수정할
     * 수 있다는 안내가 달린 별개의 상황이다. 그 밖의 상태(계약대기·수주확정·유지보수중)는
     * `NOT_IN_PROGRESS`: 아직/이미 진척을 기록할 단계가 아니라 상태를 먼저 옮겨야 한다.
     * MCP `update_progress`도 같은 서비스라 같은 거절을 받는다.
     */
    private void requireInProgress(Project project) {
        if (project.isCompleted()) {
            throw new ConflictException("PROJECT_COMPLETED",
                    "완료된 프로젝트는 진척률을 수정할 수 없습니다 — 재개 후 수정하세요");
        }

        if (project.getStatus() != ProjectStatus.IN_PROGRESS) {
            throw new ConflictException("NOT_IN_PROGRESS",
                    "진행중 프로젝트만 진척률을 수정할 수 있습니다 (현재 "
                            + project.getStatus().label() + ")");
        }
    }
}
