package kr.proten.pms.project.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.person.PersonRef;
import kr.proten.pms.project.ProjectBrief;
import kr.proten.pms.project.ProjectDetailBrief;
import kr.proten.pms.project.ProjectLookupService;
import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.ProjectQueryService;
import kr.proten.pms.project.service.dto.AssignmentView;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.dto.ProjectVisibility;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ProjectLookupService} 구현 — 가시성 판정은 기존 협력자를 그대로 쓰고,
 * 팀·부문만 PM의 소속에서 해석해 붙인다.
 *
 * <p>가시성을 여기서 다시 계산하지 않는다: {@code ProjectVisibilityService}가 정본이고
 * (상위 PRD §4-4 — 조직 가시성 ∪ 본인 배정), 두 곳에서 계산하면 챗과 화면이 서로 다른
 * 범위를 보게 된다.
 *
 * <p>PM 소속은 <b>목록 전체를 한 번에</b> 물어 붙인다 — 50건 목록이 50번의 인원 조회가
 * 되면 안 된다.
 */
@Service
@Transactional(readOnly = true)
class ProjectLookupServiceImpl implements ProjectLookupService {
    private final ProjectRepository projectRepository;
    private final ProjectVisibilityService projectVisibilityService;
    private final ProjectQueryService projectQueryService;
    private final PersonDirectoryService personDirectoryService;

    ProjectLookupServiceImpl(
            ProjectRepository projectRepository,
            ProjectVisibilityService projectVisibilityService,
            ProjectQueryService projectQueryService,
            PersonDirectoryService personDirectoryService) {
        this.projectRepository = projectRepository;
        this.projectVisibilityService = projectVisibilityService;
        this.projectQueryService = projectQueryService;
        this.personDirectoryService = personDirectoryService;
    }

    @Override
    public List<ProjectBrief> search(
            long callerPersonId, String statusLabel, String keyword, int limit) {
        ProjectVisibility visibility = projectVisibilityService.visibilityOf(callerPersonId);

        if (!visibility.unrestricted() && visibility.visibleProjectIds().isEmpty()) {
            return List.of();
        }

        List<Project> found = projectRepository.search(
                        visibility.unrestricted(),
                        // in () 은 DB마다 다르게 구는 표현이라 빈 집합을 넘기지 않는다
                        visibility.unrestricted() ? List.of(-1L) : visibility.visibleProjectIds(),
                        statusOf(statusLabel),
                        likePattern(keyword),
                        PageRequest.of(0, limit))
                .getContent();
        List<PersonRef> managers = personDirectoryService.findRefs(
                found.stream().map(Project::getManagerId).distinct().toList());

        return found.stream()
                .map(project -> toBrief(project, managerOf(managers, project.getManagerId())))
                .toList();
    }

    @Override
    public ProjectDetailBrief detail(long callerPersonId, long projectId) {
        // 가시성 밖·부재는 같은 404다 — 내부 유스케이스가 그 관문을 갖는다(A3-2)
        ProjectDetail detail = projectQueryService.getProject(callerPersonId, projectId);
        PersonRef manager = managerOf(
                personDirectoryService.findRefs(List.of(detail.managerId())), detail.managerId());

        return new ProjectDetailBrief(
                detail.id(),
                detail.name(),
                detail.client(),
                detail.status().label(),
                detail.progress(),
                detail.startDate(),
                detail.endDate(),
                detail.contractMm(),
                detail.engagement().label(),
                detail.solution(),
                manager == null ? null : manager.name(),
                participantNames(detail.assignments()),
                manager == null ? null : manager.orgUnit(),
                manager == null ? null : manager.division(),
                detail.version());
    }

    /** PM을 뺀 배정 인원의 이름 — PM은 별도 필드로 나가므로 목록에서 뺀다. */
    private static List<String> participantNames(List<AssignmentView> assignments) {
        return assignments.stream()
                .filter(assignment -> assignment.role() != ProjectRole.PM)
                .map(AssignmentView::personName)
                .toList();
    }

    private static ProjectBrief toBrief(Project project, PersonRef manager) {
        return new ProjectBrief(
                project.getId(),
                project.getName(),
                project.getClient(),
                project.getStatus().label(),
                project.getProgress(),
                project.getStartDate(),
                project.getEndDate(),
                manager == null ? null : manager.orgUnit(),
                manager == null ? null : manager.division());
    }

    private static PersonRef managerOf(List<PersonRef> refs, Long managerId) {
        return refs.stream()
                .filter(ref -> ref.id().equals(managerId))
                .findFirst()
                .orElse(null);
    }

    /** 모르는 라벨은 예외다 — 조용히 "필터 없음"으로 바꾸면 사용자가 틀린 답을 받는다. */
    private static String statusOf(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }

        return Optional.ofNullable(byLabel(label.trim()))
                .map(Enum::name)
                .orElseThrow(() -> new ValidationException(
                        "프로젝트 상태는 계약대기/수주확정/진행중/완료/유지보수중 중 하나여야 합니다",
                        "status"));
    }

    private static ProjectStatus byLabel(String label) {
        for (ProjectStatus candidate : ProjectStatus.values()) {
            if (candidate.label().equals(label)) {
                return candidate;
            }
        }

        return null;
    }

    private static String likePattern(String value) {
        return value == null || value.isBlank()
                ? null
                : "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
