package kr.proten.pms.project.service.impl;

import java.util.Optional;
import kr.proten.pms.person.service.dto.OrgPermission;
import kr.proten.pms.person.service.OrgPermissionService;
import kr.proten.pms.project.service.entity.AssignmentStatus;
import kr.proten.pms.project.service.entity.ProjectAssignment;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.springframework.stereotype.Component;

/**
 * 프로젝트 역할 해석 (상위 PRD §4-1) — 역할 판정의 단일 지점.
 *
 * 역할의 정본은 진행 중인 배정 레코드이고, 유일한 예외가 "전 프로젝트 관리"
 * 플래그다: 그 플래그를 가진 사용자는 배정 여부와 무관하게 모든 프로젝트에서 PM으로
 * 간주된다(§4-1 치환). 조직 관리자가 PM 부재·퇴사 상황을 수습할 수 있어야 하기
 * 때문이며, 이 치환 하나로 §4-2 표가 그대로 적용된다.
 */
@Component
public class ProjectRoleResolver {
    private final ProjectAssignmentRepository assignmentRepository;
    private final OrgPermissionService orgPermissionService;

    public ProjectRoleResolver(
            ProjectAssignmentRepository assignmentRepository,
            OrgPermissionService orgPermissionService) {
        this.assignmentRepository = assignmentRepository;
        this.orgPermissionService = orgPermissionService;
    }

    /** 화자의 이 프로젝트 역할 — 미배정이면 빈 값(권한 거절의 근거). */
    public Optional<ProjectRole> roleOf(long callerPersonId, long projectId) {
        if (orgPermissionService.has(callerPersonId, OrgPermission.MANAGE_ALL_PROJECTS)) {
            return Optional.of(ProjectRole.PM);
        }

        return assignmentRepository
                .findByProjectIdAndPersonIdAndStatus(projectId, callerPersonId,
                        AssignmentStatus.ACTIVE)
                .map(ProjectAssignment::getRole);
    }
}
