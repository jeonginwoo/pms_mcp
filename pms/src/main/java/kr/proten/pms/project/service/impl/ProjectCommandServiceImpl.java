package kr.proten.pms.project.service.impl;

import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.dto.CreateProjectCommand;
import kr.proten.pms.project.service.dto.AssignmentSpec;
import kr.proten.pms.project.service.ProjectCommandService;
import java.util.List;
import java.util.Objects;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.person.service.dto.OrgPermission;
import kr.proten.pms.person.service.OrgPermissionService;
import kr.proten.pms.person.service.PersonDirectoryService;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAssignment;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.service.entity.ProjectKey;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 생성 유스케이스 — AC A1-1~A1-6.
 *
 * 생성은 프로젝트가 아직 없는 행위라 프로젝트 역할이 판정 축이 될 수 없다 —
 * 권한 그룹의 "프로젝트 생성" 플래그가 유일한 판정자다(상위 PRD §4-3).
 * PM 1행 불변식과 인원 참조 검증도 이 계층의 책임이다.
 */
@Service
@Transactional
public class ProjectCommandServiceImpl implements ProjectCommandService {
    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository assignmentRepository;
    private final PersonDirectoryService personDirectoryService;
    private final OrgPermissionService orgPermissionService;
    private final AssignmentFactory assignmentFactory;
    private final ProjectAuditRecorder projectAuditRecorder;
    private final ProjectViewFactory projectViewFactory;

    public ProjectCommandServiceImpl(
            ProjectRepository projectRepository,
            ProjectAssignmentRepository assignmentRepository,
            PersonDirectoryService personDirectoryService,
            OrgPermissionService orgPermissionService,
            AssignmentFactory assignmentFactory,
            ProjectAuditRecorder projectAuditRecorder,
            ProjectViewFactory projectViewFactory) {
        this.projectRepository = projectRepository;
        this.assignmentRepository = assignmentRepository;
        this.personDirectoryService = personDirectoryService;
        this.orgPermissionService = orgPermissionService;
        this.assignmentFactory = assignmentFactory;
        this.projectAuditRecorder = projectAuditRecorder;
        this.projectViewFactory = projectViewFactory;
    }

    /**
     * 프로젝트를 만들고 지정 역할로 배정한다.
     * 검사 순서는 권한(403) → 입력 규칙(422) → 상태 충돌(409)이다 — 권한 없는
     * 호출자에게 중복 여부를 알려 주지 않으려면 권한이 가장 앞에 있어야 한다.
     */
    public ProjectDetail create(long callerPersonId, CreateProjectCommand command) {
        requireCreatePermission(callerPersonId);

        List<AssignmentSpec> assignments = command.assignments();
        Long managerId = requireExactlyOnePm(assignments);
        requireKnownPeople(assignments);

        ProjectKey key = new ProjectKey(command.client(), command.name());
        requireUniqueName(key);

        Project project = projectRepository.save(Project.create(
                key,
                command.solution(),
                command.engagement(),
                managerId,
                command.contractMm(),
                command.startDate(),
                command.endDate()));
        List<ProjectAssignment> saved = assignmentRepository.saveAll(
                assignments.stream().map(spec -> assignmentFactory.create(project, spec)).toList());
        // 생성은 이력 1건이다 (AC A1-1) — 함께 만들어진 배정은 생성 요청의 일부다
        projectAuditRecorder.created(callerPersonId, project);

        return projectViewFactory.toDetail(project, saved);
    }

    private void requireCreatePermission(long callerPersonId) {
        if (!orgPermissionService.has(callerPersonId, OrgPermission.CREATE_PROJECT)) {
            throw new ForbiddenException("프로젝트 생성 권한이 없습니다");
        }
    }

    /** 프로젝트당 role=PM 정확히 1행 불변식 (AC A1-4·A1-6). PL 복수는 정상 입력이다. */
    private Long requireExactlyOnePm(List<AssignmentSpec> assignments) {
        List<AssignmentSpec> managers = assignments.stream()
                .filter(spec -> spec.role() == ProjectRole.PM)
                .toList();

        if (managers.isEmpty()) {
            throw new UnprocessableException("PM_REQUIRED", "PM을 1명 지정해야 합니다");
        }

        if (managers.size() > 1) {
            throw new UnprocessableException("MULTIPLE_PM", "PM은 1명만 지정할 수 있습니다");
        }

        return managers.getFirst().personId();
    }

    /**
     * 인원 참조 검증 (AC A1-3).
     * ASSUMPTION: 고객사(client)는 시드 정합상 문자열이라 참조 대상 엔티티가 없다 —
     * REF_NOT_FOUND의 검증 대상은 인원 id뿐이다. 고객사가 엔티티가 되면 함께 검증한다.
     */
    private void requireKnownPeople(List<AssignmentSpec> assignments) {
        List<Long> unknownIds = assignments.stream()
                .map(AssignmentSpec::personId)
                .filter(Objects::nonNull)
                .distinct()
                .filter(personId -> !personDirectoryService.existsActive(personId))
                .toList();

        if (!unknownIds.isEmpty()) {
            throw new UnprocessableException("REF_NOT_FOUND", "없는 인원입니다: " + unknownIds);
        }
    }

    /** 중복 이름 검사 (AC A1-2) — 정규화 후 비교하고 soft 삭제분은 제외한다. */
    private void requireUniqueName(ProjectKey key) {
        boolean duplicated =
                projectRepository.existsByNormalizedClientAndNormalizedNameAndDeletedFalse(
                        key.normalizedClient(), key.normalizedName());

        if (duplicated) {
            throw new ConflictException("DUPLICATE_NAME", "같은 고객사에 같은 이름의 프로젝트가 있습니다");
        }
    }
}
