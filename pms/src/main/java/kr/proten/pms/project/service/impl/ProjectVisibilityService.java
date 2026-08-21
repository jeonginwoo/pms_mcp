package kr.proten.pms.project.service.impl;

import kr.proten.pms.project.service.dto.ProjectVisibility;
import java.util.List;
import java.util.Set;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.person.service.dto.OrgVisibility;
import kr.proten.pms.person.service.OrgVisibilityService;
import kr.proten.pms.project.service.entity.AssignmentStatus;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.repository.ProjectRepository;
import org.springframework.stereotype.Component;

/**
 * 프로젝트 가시성 판정 (상위 PRD §4-4) — 조회·쓰기 유스케이스가 공유하는 단일 지점.
 *
 * ASSUMPTION: 프로젝트는 조직 노드를 직접 갖지 않으므로(PRD-pms §4 필드 목록)
 * 조직 가시성을 배정 인원으로 옮겨 판정한다 — 가시 인원이 한 명이라도 배정된
 * 프로젝트가 "자기 팀·부문 범위"다. 본인 배정 프로젝트가 타 팀이어도 보이는
 * 이유는 조직 가시성이 본인을 항상 포함하기 때문이며, 이것이 AC A3-1의 합집합을
 * 그대로 만든다. 프로젝트에 조직 귀속 컬럼이 생기면 이 규칙을 재검토한다.
 */
@Component
public class ProjectVisibilityService {
    private final OrgVisibilityService orgVisibilityService;
    private final ProjectAssignmentRepository assignmentRepository;
    private final ProjectRepository projectRepository;

    public ProjectVisibilityService(
            OrgVisibilityService orgVisibilityService,
            ProjectAssignmentRepository assignmentRepository,
            ProjectRepository projectRepository) {
        this.orgVisibilityService = orgVisibilityService;
        this.assignmentRepository = assignmentRepository;
        this.projectRepository = projectRepository;
    }

    /** 화자가 볼 수 있는 프로젝트 범위. */
    public ProjectVisibility visibilityOf(long callerPersonId) {
        OrgVisibility orgVisibility = orgVisibilityService.visibilityOf(callerPersonId);

        if (orgVisibility.unrestricted()) {
            return ProjectVisibility.all();
        }

        return ProjectVisibility.of(projectIdsAssignedTo(orgVisibility.visiblePersonIds()));
    }

    /**
     * 단건 조회의 관문 — 부재(soft 삭제 포함)와 가시성 밖을 같은 404로 수렴시킨다.
     * 두 사유를 구분해 알려 주면 "없다"와 "못 본다"가 갈라져 존재가 드러난다.
     */
    public Project requireVisible(long callerPersonId, long projectId) {
        Project project = projectRepository.findByIdAndDeletedFalse(projectId)
                .orElseThrow(NotFoundException::new);

        if (!visibilityOf(callerPersonId).canView(projectId)) {
            throw new NotFoundException();
        }

        return project;
    }

    /** id를 정렬해 넘겨 질의 파라미터를 결정적으로 유지한다. */
    private Set<Long> projectIdsAssignedTo(Set<Long> personIds) {
        List<Long> sortedPersonIds = personIds.stream().sorted().toList();

        return Set.copyOf(assignmentRepository.findDistinctProjectIdsByPersonIds(
                sortedPersonIds, AssignmentStatus.ACTIVE));
    }
}
