package kr.proten.pms.project.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.StaleVersionException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.project.repository.ProjectPermissionOverrideRepository;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.ProjectPermissionService;
import kr.proten.pms.project.service.dto.ProjectPermissionMatrix;
import kr.proten.pms.project.service.dto.UpdateProjectPermissionsCommand;
import kr.proten.pms.project.service.entity.EffectiveProjectPermissions;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAction;
import kr.proten.pms.project.service.entity.ProjectPermissionOverride;
import kr.proten.pms.project.service.entity.ProjectPermissionRules;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트별 권한 매트릭스 유스케이스 — AC A8-1~A8-4 · A8-7.
 *
 * <p>저장 순서가 AC 두 줄로 정해진다: A8-4가 <b>"아무것도 안 바뀜"</b>을 요구하므로
 * 고정 칸 검증이 저장보다 앞이고, A8-2가 <b>전체 교체</b>이므로 삭제·재삽입이 한
 * 트랜잭션이다. 그 앞은 다른 쓰기 경로와 같다 — 가시성(404) → 권한(403) →
 * version(409).
 */
@Service
@Transactional
public class ProjectPermissionServiceImpl implements ProjectPermissionService {

    private final ProjectRepository projectRepository;
    private final ProjectPermissionOverrideRepository overrideRepository;
    private final ProjectVisibilityService projectVisibilityService;
    private final ProjectRoleResolver projectRoleResolver;
    private final ProjectPermissionMatrixResolver matrixResolver;
    private final ProjectAuditRecorder projectAuditRecorder;

    public ProjectPermissionServiceImpl(
            ProjectRepository projectRepository,
            ProjectPermissionOverrideRepository overrideRepository,
            ProjectVisibilityService projectVisibilityService,
            ProjectRoleResolver projectRoleResolver,
            ProjectPermissionMatrixResolver matrixResolver,
            ProjectAuditRecorder projectAuditRecorder) {
        this.projectRepository = projectRepository;
        this.overrideRepository = overrideRepository;
        this.projectVisibilityService = projectVisibilityService;
        this.projectRoleResolver = projectRoleResolver;
        this.matrixResolver = matrixResolver;
        this.projectAuditRecorder = projectAuditRecorder;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectPermissionMatrix getMatrix(long callerPersonId, long projectId) {
        Project project = projectVisibilityService.requireVisible(callerPersonId, projectId);

        return matrixOf(projectId, project.getVersion(), matrixResolver.of(projectId));
    }

    @Override
    public ProjectPermissionMatrix updateOverrides(
            long callerPersonId, long projectId, UpdateProjectPermissionsCommand command) {
        // 가시성 밖·부재는 같은 404다 (A3-2) — 반환값은 쓰지 않는다, 이 호출은 그 판정이다
        projectVisibilityService.requireVisible(callerPersonId, projectId);
        requireManager(callerPersonId, projectId);
        requireEditableCells(command.overrides());
        requireDistinctCells(command.overrides());

        /*
         * version 검사와 증가가 한 문장이다 — `where version = :expected`가 곧 검사다.
         * 매트릭스는 별도 표에 저장돼 `projects` 행이 더러워지지 않으므로, 이것을 하지
         * 않으면 낙관적 락이 아무것도 막지 못한다(두 PM이 같은 version으로 각자 저장하고
         * 나중 것이 상대 매트릭스를 통째로 덮는다 — 전체 교체다).
         *
         * 새 version이 `expected + 1`로 **결정되는 것**이 요점이다: 락 모드로 미루면
         * 이 트랜잭션 안에서는 증가 전 값밖에 없고, 그것을 응답에 실으면 클라이언트가
         * 위반한 적 없는 락에 걸려 409를 받는다(§7 왕복 — 저장소 javadoc 참조).
         */
        if (projectRepository.bumpVersion(projectId, command.version()) == 0) {
            throw new StaleVersionException("다른 사용자가 먼저 수정했습니다");
        }
        long version = command.version() + 1;

        Map<String, Object> before = snapshot(matrixResolver.of(projectId));
        replaceOverrides(projectId, command.overrides());
        EffectiveProjectPermissions merged = matrixResolver.of(projectId);
        projectAuditRecorder.permissionsChanged(
                callerPersonId, projectId, before, snapshot(merged));

        return matrixOf(projectId, version, merged);
    }

    /** 조정은 PM만 — ADMIN은 §4-1 치환으로 `ProjectRoleResolver`가 이미 포함한다 (A8-3) */
    private void requireManager(long callerPersonId, long projectId) {
        projectRoleResolver.roleOf(callerPersonId, projectId)
                .filter(ProjectRole.PM::equals)
                .orElseThrow(() -> new ForbiddenException("담당자만 가능"));
    }

    /**
     * 고정 칸이 하나라도 섞이면 <b>아무것도 하기 전에</b> 422다 (A8-4).
     * 유효 action은 §4의 4종이므로 {@code HANDOVER}를 담은 요청도 여기서 걸린다.
     */
    private void requireEditableCells(List<UpdateProjectPermissionsCommand.Override> overrides) {
        for (UpdateProjectPermissionsCommand.Override override : overrides) {
            if (!ProjectPermissionRules.editable(override.role(), override.action())) {
                throw new UnprocessableException(ErrorCode.IMMUTABLE_PERMISSION,
                        "고정된 권한 칸은 조정할 수 없습니다");
            }
        }
    }

    /**
     * 전체 교체 (A8-2) — <b>기본값과 같은 값은 저장하지 않는다</b>.
     *
     * <p>기본값을 행으로 남기면 §4-2 표가 바뀌는 날 그 행이 옛 기본값을 들고 있어
     * 표와 데이터가 조용히 갈린다. 그래서 "행이 없다 = 기본값"이 불변식이고,
     * {@code overrides: []}는 전량 삭제로 끝난다(별도 복원 API가 없는 이유다).
     */
    private void replaceOverrides(
            long projectId, List<UpdateProjectPermissionsCommand.Override> overrides) {
        overrideRepository.deleteByProjectId(projectId);
        // 삭제를 먼저 반영해야 유니크 제약(project_id, role, action)이 재삽입과 부딪히지 않는다
        overrideRepository.flush();

        List<ProjectPermissionOverride> rows = new ArrayList<>();
        for (UpdateProjectPermissionsCommand.Override override : overrides) {
            if (override.allowed()
                    != ProjectPermissionRules.allowedByDefault(override.role(), override.action())) {
                rows.add(ProjectPermissionOverride.of(
                        projectId, override.role(), override.action(), override.allowed()));
            }
        }
        overrideRepository.saveAll(rows);
        overrideRepository.flush();
    }

    /**
     * 같은 칸이 두 번 담긴 요청은 <b>400</b>이다 (§7 봉투).
     *
     * <p>막지 않으면 유니크 제약(`uq_project_permission_cell`)에 부딪혀
     * {@code DataIntegrityViolationException} → 전역 핸들러의 catch-all → <b>500</b>이 된다.
     * 호출자가 고칠 수 있는 요청 오류를 서버 장애로 보고하는 자리다(2026-08-22 선례).
     */
    private void requireDistinctCells(List<UpdateProjectPermissionsCommand.Override> overrides) {
        Set<String> seen = new HashSet<>();
        for (UpdateProjectPermissionsCommand.Override override : overrides) {
            if (!seen.add(override.role().name() + "." + override.action().name())) {
                throw new ValidationException("overrides",
                        "같은 칸을 두 번 담을 수 없습니다: "
                                + override.role() + " · " + override.action());
            }
        }
    }

    /** 고정 칸도 빠짐없이 담는다 — 화면이 잠금 표시를 그려야 한다(A8-1) */
    private ProjectPermissionMatrix matrixOf(
            long projectId, long version, EffectiveProjectPermissions merged) {
        List<ProjectPermissionMatrix.Cell> cells = new ArrayList<>();
        for (ProjectRole role : ProjectRole.values()) {
            for (ProjectAction action : ProjectAction.values()) {
                cells.add(new ProjectPermissionMatrix.Cell(
                        role.name(),
                        action.name(),
                        merged.allows(role, action),
                        ProjectPermissionRules.editable(role, action),
                        merged.isOverridden(role, action)));
            }
        }

        return new ProjectPermissionMatrix(projectId, cells, version);
    }

    /**
     * 감사 스냅샷 — 조정 가능한 8칸의 <b>유효값</b>이다.
     * 저장된 override만 찍으면 기본값으로 되돌린 칸이 diff에서 사라진다.
     */
    private Map<String, Object> snapshot(EffectiveProjectPermissions merged) {
        Map<String, Object> state = new LinkedHashMap<>();
        for (ProjectRole role : ProjectRole.values()) {
            for (ProjectAction action : ProjectAction.values()) {
                if (ProjectPermissionRules.editable(role, action)) {
                    state.put(role.name() + "." + action.name(), merged.allows(role, action));
                }
            }
        }

        return state;
    }
}
