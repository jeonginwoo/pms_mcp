package kr.proten.pms.project.service.impl;

import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.project.service.entity.ProjectAction;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.springframework.stereotype.Component;

/**
 * 프로젝트 기능별 권한 판정 (상위 PRD §4-2 기본 매트릭스) — 판정의 단일 지점.
 *
 * 판정을 여기 한 곳에 두는 이유: 같은 규칙이 유스케이스마다 흩어지면(진척률은
 * 배정 전원, 정보 수정은 PM·PL, 배정은 PM) 한 칸만 바뀌어도 어디를 고쳐야 하는지
 * 알 수 없다.
 *
 * <p><b>2026-08-26(US-A8)부터 표는 이 클래스가 갖지 않는다</b>: §4-2 기본값은
 * {@code ProjectPermissionRules}가, 프로젝트별 override 병합은
 * {@code ProjectPermissionMatrixResolver}가 든다 — A8-1 조회 응답이 같은 병합을
 * 읽어야 하므로, 여기 표를 두면 화면이 "할 수 있다"고 그린 칸에서 서버가 403을 내는
 * 어긋남이 생긴다. 예고대로 <b>호출부는 한 줄도 바뀌지 않았다</b>.
 *
 * ADMIN 치환(§4-1: "전 프로젝트 관리" 플래그 보유자는 모든 프로젝트에서 PM)은
 * {@link ProjectRoleResolver}가 이미 처리하므로 여기서 다시 다루지 않는다.
 */
@Component
class ProjectActionPermission {
    private final ProjectRoleResolver projectRoleResolver;
    private final OrgPermissionService orgPermissionService;
    private final ProjectPermissionMatrixResolver matrixResolver;

    ProjectActionPermission(
            ProjectRoleResolver projectRoleResolver,
            OrgPermissionService orgPermissionService,
            ProjectPermissionMatrixResolver matrixResolver) {
        this.projectRoleResolver = projectRoleResolver;
        this.orgPermissionService = orgPermissionService;
        this.matrixResolver = matrixResolver;
    }

    /**
     * 허용되지 않으면 403으로 거절한다 (conventions §4 — 쓰기 권한 없음).
     * 미배정도 같은 403이다: 가시성 밖이었다면 호출자는 이 판정에 닿기 전에
     * 404로 막혔어야 한다(404 은닉은 가시성 판정의 몫 — A2-4·A7-5).
     */
    void require(long callerPersonId, long projectId, ProjectAction action) {
        boolean allowed = projectRoleResolver.roleOf(callerPersonId, projectId)
                .filter(role -> matrixResolver.allows(projectId, role, action))
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
