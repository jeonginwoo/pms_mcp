package kr.proten.pms.project.service.impl;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.project.service.entity.ProjectAction;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.springframework.stereotype.Component;

/**
 * 프로젝트 기능별 권한 판정 (상위 PRD §4-2 기본 매트릭스) — 판정의 단일 지점.
 *
 * 표를 여기 한 곳에 두는 이유: 같은 규칙이 유스케이스마다 흩어지면(진척률은
 * 배정 전원, 정보 수정은 PM·PL, 배정은 PM) 한 칸만 바뀌어도 어디를 고쳐야 하는지
 * 알 수 없다. 프로젝트별 권한 커스텀(US-A8)이 들어오면 이 클래스가 기본값 위에
 * override를 병합하는 자리가 된다 — 호출부는 그대로다.
 *
 * ADMIN 치환(§4-1: "전 프로젝트 관리" 플래그 보유자는 모든 프로젝트에서 PM)은
 * {@link ProjectRoleResolver}가 이미 처리하므로 여기서 다시 다루지 않는다.
 */
@Component
class ProjectActionPermission {
    // 상위 PRD §4-2 기본값 — 완료·재개는 진척률과 같은 실무 경로라 배정 전원이다
    private static final Map<ProjectAction, Set<ProjectRole>> DEFAULTS = Map.of(
            ProjectAction.EDIT_INFO, EnumSet.of(ProjectRole.PM, ProjectRole.PL),
            ProjectAction.ASSIGN, EnumSet.of(ProjectRole.PM),
            ProjectAction.PROGRESS,
            EnumSet.of(ProjectRole.PM, ProjectRole.PL, ProjectRole.PARTICIPANT),
            ProjectAction.COMPLETE_REOPEN,
            EnumSet.of(ProjectRole.PM, ProjectRole.PL, ProjectRole.PARTICIPANT),
            // 이관은 PM 하나다(D1) — 완료·재개와 달리 실무 경로가 아니라
            // 프로젝트를 유지보수로 넘기는 마지막 결정이다
            ProjectAction.HANDOVER, EnumSet.of(ProjectRole.PM));

    private final ProjectRoleResolver projectRoleResolver;
    private final OrgPermissionService orgPermissionService;

    ProjectActionPermission(
            ProjectRoleResolver projectRoleResolver,
            OrgPermissionService orgPermissionService) {
        this.projectRoleResolver = projectRoleResolver;
        this.orgPermissionService = orgPermissionService;
    }

    /**
     * 허용되지 않으면 403으로 거절한다 (conventions §4 — 쓰기 권한 없음).
     * 미배정도 같은 403이다: 가시성 밖이었다면 호출자는 이 판정에 닿기 전에
     * 404로 막혔어야 한다(404 은닉은 가시성 판정의 몫 — A2-4·A7-5).
     */
    void require(long callerPersonId, long projectId, ProjectAction action) {
        boolean allowed = projectRoleResolver.roleOf(callerPersonId, projectId)
                .filter(role -> DEFAULTS.get(action).contains(role))
                .isPresent();

        if (!allowed) {
            throw new ForbiddenException("담당자만 가능");
        }
    }

    /**
     * 삭제 권한 (AC A4-1·A4-2 + 2026-08-22 결정) — **PM 또는 "프로젝트 생성" 플래그**.
     *
     * 표(§4-2)의 삭제 행은 프로젝트별 커스텀이 불가한 고정 행이라 {@link ProjectAction}에
     * 두지 않는다. 대신 판정 축이 둘(프로젝트 역할 · 조직 기능 플래그)이라 여기에서
     * 합집합으로 답한다 — 만든 사람(생성 권한자)이 지울 수 있어야 한다는 요구를
     * 반영한 확장이고, 참여자·PL은 여전히 403이다.
     */
    void requireDelete(long callerPersonId, long projectId) {
        if (orgPermissionService.has(callerPersonId, OrgPermission.CREATE_PROJECT)) {
            return;
        }

        boolean manager = projectRoleResolver.roleOf(callerPersonId, projectId)
                .filter(ProjectRole.PM::equals)
                .isPresent();

        if (!manager) {
            throw new ForbiddenException("담당자만 가능");
        }
    }
}
