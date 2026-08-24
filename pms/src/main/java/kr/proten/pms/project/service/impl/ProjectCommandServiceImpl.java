package kr.proten.pms.project.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.ProjectCommandService;
import kr.proten.pms.project.service.dto.AssignmentSpec;
import kr.proten.pms.project.service.dto.CreateProjectCommand;
import kr.proten.pms.project.service.dto.EditProjectCommand;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAction;
import kr.proten.pms.project.AssignmentChanged;
import kr.proten.pms.project.service.entity.ProjectAssignment;
import kr.proten.pms.project.service.entity.ProjectKey;
import kr.proten.pms.project.service.entity.ProjectRole;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 CRUD 유스케이스 — AC A1 · A4 · A5.
 *
 * 세 행위의 판정 축이 다르다: 생성은 프로젝트가 아직 없어 프로젝트 역할이 축이 될 수
 * 없으므로 권한 그룹의 "프로젝트 생성" 플래그가 유일한 판정자이고(상위 PRD §4-3),
 * 수정·삭제는 대상이 이미 있으므로 가시성이 가장 앞에 온다.
 *
 * 검사 순서가 이 클래스의 규칙이다: (있는 대상이면) 가시성 404 은닉 → 권한 403 →
 * version 409 → 도메인 규칙 409·422. 가시성이 앞인 이유는 범위 밖 프로젝트에 권한이나
 * 상태를 알려 주면 존재가 드러나기 때문이고, 생성에서 권한이 중복 검사보다 앞인 이유도
 * 같다 — 권한 없는 호출자에게 어떤 이름이 이미 있는지 알려 주지 않는다.
 */
@Service
@Transactional
public class ProjectCommandServiceImpl implements ProjectCommandService {
    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository assignmentRepository;
    private final ProjectVisibilityService projectVisibilityService;
    private final ProjectActionPermission projectActionPermission;
    private final PersonDirectoryService personDirectoryService;
    private final OrgPermissionService orgPermissionService;
    private final AssignmentFactory assignmentFactory;
    private final ProjectAuditRecorder projectAuditRecorder;
    private final ProjectViewFactory projectViewFactory;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public ProjectCommandServiceImpl(
            ProjectRepository projectRepository,
            ProjectAssignmentRepository assignmentRepository,
            ProjectVisibilityService projectVisibilityService,
            ProjectActionPermission projectActionPermission,
            PersonDirectoryService personDirectoryService,
            OrgPermissionService orgPermissionService,
            AssignmentFactory assignmentFactory,
            ProjectAuditRecorder projectAuditRecorder,
            ProjectViewFactory projectViewFactory,
            Clock clock,
            ApplicationEventPublisher events) {
        this.projectRepository = projectRepository;
        this.assignmentRepository = assignmentRepository;
        this.projectVisibilityService = projectVisibilityService;
        this.projectActionPermission = projectActionPermission;
        this.personDirectoryService = personDirectoryService;
        this.orgPermissionService = orgPermissionService;
        this.assignmentFactory = assignmentFactory;
        this.projectAuditRecorder = projectAuditRecorder;
        this.projectViewFactory = projectViewFactory;
        this.clock = clock;
        this.events = events;
    }

    /** 프로젝트를 만들고 지정 역할로 배정한다 (AC A1-1~A1-6). */
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
        // 이력은 1건이지만 **이벤트는 배정마다**다 (§8 MemberAssignedToProject):
        // 프로젝트를 만들며 붙인 배정도 배정이고, 그것이 가동률을 바꾸고 알림을 만든다.
        // 이 경로가 빠져 있으면 "새 프로젝트로 과부하가 된 사람"을 아무도 모른다(실측 발견)
        saved.forEach(assignment -> events.publishEvent(new AssignmentChanged(
                AssignmentChanged.Kind.ASSIGNED,
                project.getId(),
                project.getName(),
                assignment.getPersonId(),
                AssignmentChanged.monthsOf(assignment.getStartDate(), assignment.getEndDate(),
                        YearMonth.now(clock)))));

        return projectViewFactory.toDetail(project, saved);
    }

    /**
     * 정보 수정 + 순방향 한 칸 전이 (AC A5-1~A5-3).
     * 상태 전이 규칙은 엔티티가 갖는다(§5) — 역방향은 이 경로로 불가하고, 완료·재개는
     * 전용 경로(ProjectLifecycleService)만 쓴다.
     */
    public ProjectDetail edit(long callerPersonId, EditProjectCommand command) {
        Project project =
                projectVisibilityService.requireVisible(callerPersonId, command.projectId());
        projectActionPermission.require(callerPersonId, project.getId(),
                ProjectAction.EDIT_INFO);
        project.requireVersion(command.version());

        Map<String, Object> before = projectAuditRecorder.snapshot(project);
        // 질의(중복 검사)는 엔티티를 바꾸기 **전에** 끝낸다 — 더티 상태에서 질의하면
        // JPA가 먼저 flush 하면서 version이 한 번 더 올라간다(수정 1회 = +2)
        ProjectKey key = new ProjectKey(command.client(), command.name());
        requireUniqueNameExcluding(key, project.getId());
        project.advanceStatusTo(command.status());
        project.editInfo(
                key,
                command.solution(),
                command.engagement(),
                command.contractMm(),
                command.startDate(),
                command.endDate());

        Project saved = projectRepository.saveAndFlush(project);
        projectAuditRecorder.changed(callerPersonId, saved, before);

        return projectViewFactory.toDetail(saved);
    }

    /**
     * 소프트 삭제 (AC A4-1·A4-2).
     * version을 받지 않는다 — 삭제는 "어떤 값으로 바꿀지"가 없는 행위라 동시 수정
     * 충돌의 대상이 아니다.
     */
    public void delete(long callerPersonId, long projectId) {
        Project project = projectVisibilityService.requireVisible(callerPersonId, projectId);
        projectActionPermission.requireDelete(callerPersonId, projectId);

        Map<String, Object> before = projectAuditRecorder.snapshot(project);
        project.delete();
        projectAuditRecorder.deleted(callerPersonId, projectRepository.saveAndFlush(project),
                before);
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
            throw new UnprocessableException(ErrorCode.PM_REQUIRED, "PM을 1명 지정해야 합니다");
        }

        if (managers.size() > 1) {
            throw new UnprocessableException(ErrorCode.MULTIPLE_PM, "PM은 1명만 지정할 수 있습니다");
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
            throw new UnprocessableException(ErrorCode.REF_NOT_FOUND, "없는 인원입니다: " + unknownIds);
        }
    }

    /** 중복 이름 검사 (AC A1-2) — 정규화 후 비교하고 soft 삭제분은 제외한다. */
    private void requireUniqueName(ProjectKey key) {
        boolean duplicated =
                projectRepository.existsByNormalizedClientAndNormalizedNameAndDeletedFalse(
                        key.normalizedClient(), key.normalizedName());

        if (duplicated) {
            throw new ConflictException(ErrorCode.DUPLICATE_NAME, "같은 고객사에 같은 이름의 프로젝트가 있습니다");
        }
    }

    /** 수정 시의 중복 검사 — 자기 자신은 대상에서 뺀다(이름을 안 바꾸는 수정). */
    private void requireUniqueNameExcluding(ProjectKey key, Long projectId) {
        boolean duplicated = projectRepository
                .existsByNormalizedClientAndNormalizedNameAndDeletedFalseAndIdNot(
                        key.normalizedClient(), key.normalizedName(), projectId);

        if (duplicated) {
            throw new ConflictException(ErrorCode.DUPLICATE_NAME, "같은 고객사에 같은 이름의 프로젝트가 있습니다");
        }
    }
}
