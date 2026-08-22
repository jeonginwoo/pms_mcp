package kr.proten.pms;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import kr.proten.pms.audit.AuditAction;
import kr.proten.pms.audit.AuditSource;
import kr.proten.pms.audit.repository.AuditLogRepository;
import kr.proten.pms.audit.service.entity.AuditLog;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.entity.Grade;
import kr.proten.pms.person.service.entity.PersonFixtures;
import kr.proten.pms.person.service.entity.VisibilityScope;
import kr.proten.pms.project.service.AssignmentService;
import kr.proten.pms.project.service.ProjectCommandService;
import kr.proten.pms.project.service.ProjectLifecycleService;
import kr.proten.pms.project.service.ProjectQueryService;
import kr.proten.pms.project.service.dto.AssignmentSpec;
import kr.proten.pms.project.service.dto.AssignmentView;
import kr.proten.pms.project.service.dto.CreateAssignmentCommand;
import kr.proten.pms.project.service.dto.CreateProjectCommand;
import kr.proten.pms.project.service.dto.EditProjectCommand;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.dto.ProjectSummary;
import kr.proten.pms.project.service.dto.UpdateAssignmentCommand;
import kr.proten.pms.project.service.dto.UpdateProgressCommand;
import kr.proten.pms.project.service.entity.Engagement;
import kr.proten.pms.project.service.entity.ProjectRole;
import kr.proten.pms.project.service.entity.ProjectStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * 생애주기 관통 통합 검증 — 실물 PostgreSQL (A5 수정·전이 · A7 완료/재개 ·
 * EPIC B 배정 CRUD · EPIC G 감사 기록).
 *
 * 여기서만 확인할 수 있는 것: 전이가 실물 version과 함께 커밋되는지, 감사 행이
 * 같은 트랜잭션에서 실제로 쌓이는지(JSON 스냅샷·source·projectId 포함), 그리고
 * 배정 종료가 역할 판정 모집단에서 빠지는지. 규칙 자체는 단위 테스트가 본다.
 *
 * 다른 클래스와 컨테이너·컨텍스트를 공유하므로 인원은 AX사업기획부에, 식별자는
 * 300번대에 둔다(팀 범위를 정확히 단정하는 다른 테스트를 깨지 않기 위해).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectLifecycleIntegrationTest extends PostgresTestBase {
    private static final long ADMIN_GROUP_ID = 31L;
    private static final long MEMBER_GROUP_ID = 32L;

    private static final long PM_ID = 301L;
    private static final long MEMBER_ID = 302L;
    private static final long NEW_MEMBER_ID = 303L;
    private static final long OUTSIDER_ID = 304L;

    @Autowired
    private OrgUnitRepository orgUnitRepository;
    @Autowired
    private GradeRepository gradeRepository;
    @Autowired
    private PermissionGroupRepository permissionGroupRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private ProjectCommandService projectCommandService;
    @Autowired
    private ProjectQueryService projectQueryService;
    @Autowired
    private ProjectLifecycleService projectLifecycleService;
    @Autowired
    private AssignmentService assignmentService;

    @BeforeAll
    void seedFixture() {
        orgUnitRepository.saveAll(PersonFixtures.orgUnits());
        gradeRepository.save(Grade.of(1L, "수석", 1.5));
        permissionGroupRepository.saveAll(List.of(
                PersonFixtures.group(ADMIN_GROUP_ID, "생애주기관리자", VisibilityScope.COMPANY,
                        OrgPermission.CREATE_PROJECT),
                PersonFixtures.group(MEMBER_GROUP_ID, "생애주기팀원", VisibilityScope.SELF)));
        personRepository.saveAll(List.of(
                PersonFixtures.person(PM_ID, "생애주기피엠", PersonFixtures.OTHER_DIVISION_ID,
                        ADMIN_GROUP_ID),
                PersonFixtures.person(MEMBER_ID, "생애주기팀원", PersonFixtures.OTHER_DIVISION_ID,
                        MEMBER_GROUP_ID),
                PersonFixtures.person(NEW_MEMBER_ID, "생애주기신규", PersonFixtures.OTHER_DIVISION_ID,
                        MEMBER_GROUP_ID),
                PersonFixtures.person(OUTSIDER_ID, "생애주기외부", PersonFixtures.CS_TEAM_ID,
                        MEMBER_GROUP_ID)));
    }

    @Test
    @DisplayName("A5·A7 — 계약대기부터 완료·재개까지 실물 version으로 이어진다")
    void lifecycle_fromPendingToCompletedAndReopened() {
        ProjectDetail created = createProject("생애주기 관통");

        // 계약대기 → 수주확정 → 진행중 (A5-1: 순방향 한 칸씩만)
        ProjectDetail confirmed = projectCommandService.edit(PM_ID,
                editCommand(created.id(), "생애주기 관통", ProjectStatus.ORDER_CONFIRMED,
                        created.version()));
        assertThat(confirmed.status()).isEqualTo(ProjectStatus.ORDER_CONFIRMED);

        ProjectDetail started = projectCommandService.edit(PM_ID,
                editCommand(created.id(), "생애주기 관통", ProjectStatus.IN_PROGRESS,
                        confirmed.version()));
        assertThat(started.status()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(started.phase().name()).isEqualTo("SOLUTION");
        // 수정 1회 = version +1 — 변경 후에 질의를 하면 flush가 끼어들어 두 칸씩 오른다
        assertThat(confirmed.version()).isEqualTo(created.version() + 1);
        assertThat(started.version()).isEqualTo(confirmed.version() + 1);

        // 진척률 100 — 상태는 그대로이고 완료 처리 가능만 알린다 (A2-3)
        var progress = projectLifecycleService.updateProgress(PM_ID,
                new UpdateProgressCommand(created.id(), 100, started.version(), true));
        assertThat(progress.completable()).isTrue();

        // 완료 처리 (A7-1) → 재개 (A7-3: 진척률 90 복귀)
        ProjectDetail completed = projectLifecycleService.complete(
                PM_ID, created.id(), progress.version());
        assertThat(completed.status()).isEqualTo(ProjectStatus.COMPLETED);

        ProjectDetail reopened = projectLifecycleService.reopen(
                PM_ID, created.id(), completed.version());
        assertThat(reopened.status()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(reopened.progress()).isEqualTo(90);
    }

    @Test
    @DisplayName("G1-1·G2-1 — 생성·전이·진척률이 감사 행으로 쌓인다 (JSON·source 포함)")
    void audit_accumulatesRowsPerChange() {
        ProjectDetail created = createProject("감사 기록 확인");
        // 진척률은 진행중에서만 수정되므로(2026-08-22) 전이 두 칸이 먼저 쌓인다
        ProjectDetail started = advanceToInProgress(created);
        projectLifecycleService.updateProgress(PM_ID,
                new UpdateProgressCommand(created.id(), 30, started.version(), true));

        List<AuditLog> logs = auditOf(created.id());

        assertThat(logs).map(AuditLog::getAction).containsExactly(
                AuditAction.CREATE, AuditAction.STATE_CHANGE, AuditAction.STATE_CHANGE,
                AuditAction.UPDATE);
        assertThat(logs).allSatisfy(log -> {
            assertThat(log.getEntityType()).isEqualTo("Project");
            assertThat(log.getProjectId()).isEqualTo(created.id());
            assertThat(log.getActorId()).isEqualTo(PM_ID);
            // 요청 밖(MCP 어댑터 부재)이 아니라 웹 경로로 남는다
            assertThat(log.getSource()).isEqualTo(AuditSource.WEB);
            assertThat(log.getCreatedAt()).isNotNull();
        });

        AuditLog creation = logs.getFirst();
        assertThat(creation.getBeforeState()).isNull();
        assertThat(creation.getAfterState())
                .contains("\"name\":\"감사 기록 확인\"")
                .contains("\"status\":\"CONTRACT_PENDING\"")
                .contains("\"startDate\":\"2026-08-01\"");

        AuditLog transition = logs.get(1);
        assertThat(transition.getBeforeState()).isEqualTo("{\"status\":\"CONTRACT_PENDING\"}");
        assertThat(transition.getAfterState()).isEqualTo("{\"status\":\"ORDER_CONFIRMED\"}");

        AuditLog progressChange = logs.getLast();
        assertThat(progressChange.getBeforeState()).isEqualTo("{\"progress\":0}");
        assertThat(progressChange.getAfterState()).isEqualTo("{\"progress\":30}");
    }

    @Test
    @DisplayName("B1·B2 — 배정 추가·수정·종료가 이어지고 이력이 projectId로 묶인다")
    void assignment_crudAndAuditTrail() {
        ProjectDetail created = createProject("배정 관통");

        AssignmentView assigned = assignmentService.assign(PM_ID, new CreateAssignmentCommand(
                created.id(), NEW_MEMBER_ID, ProjectRole.PARTICIPANT, null, null, 0.4));
        assertThat(assigned.id()).isNotNull();
        // 기간 미지정은 프로젝트 기간으로 채워진다 (A6-6)
        assertThat(assigned.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));

        AssignmentView updated = assignmentService.update(PM_ID, new UpdateAssignmentCommand(
                assigned.id(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31), 0.9,
                assigned.version()));
        assertThat(updated.monthlyMm()).isEqualTo(0.9);

        // 상세는 진행 중 배정만 싣는다 — 종료 전 3명(PM·팀원·신규)
        assertThat(projectQueryService.getProject(PM_ID, created.id()).assignments()).hasSize(3);

        assignmentService.close(PM_ID, assigned.id());
        assertThat(projectQueryService.getProject(PM_ID, created.id()).assignments()).hasSize(2);

        List<AuditLog> assignmentLogs = auditOf(created.id()).stream()
                .filter(log -> "ProjectAssignment".equals(log.getEntityType()))
                .toList();
        assertThat(assignmentLogs).map(AuditLog::getAction).containsExactly(
                AuditAction.CREATE, AuditAction.UPDATE, AuditAction.DELETE);
        assertThat(assignmentLogs).allSatisfy(log ->
                assertThat(log.getEntityId()).isEqualTo(assigned.id()));
        assertThat(assignmentLogs.getLast().getAfterState()).contains("\"status\":\"CLOSED\"");
    }

    @Test
    @DisplayName("B1-2 — 종료된 배정은 재배정을 막지 않는다 (키는 종료 아님 기준)")
    void assign_afterClose_isAllowedAgain() {
        ProjectDetail created = createProject("재배정 확인");
        AssignmentView first = assignmentService.assign(PM_ID, new CreateAssignmentCommand(
                created.id(), NEW_MEMBER_ID, ProjectRole.PARTICIPANT, null, null, 0.4));

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> assignmentService.assign(PM_ID, new CreateAssignmentCommand(
                        created.id(), NEW_MEMBER_ID, ProjectRole.PARTICIPANT, null, null, 0.4)))
                .satisfies(thrown ->
                        assertThat(thrown.code()).isEqualTo(ErrorCode.DUPLICATE_ASSIGNMENT));

        assignmentService.close(PM_ID, first.id());

        AssignmentView again = assignmentService.assign(PM_ID, new CreateAssignmentCommand(
                created.id(), NEW_MEMBER_ID, ProjectRole.PL, null, null, 0.2));
        assertThat(again.id()).isNotEqualTo(first.id());
    }

    @Test
    @DisplayName("A5-3·B1-4 — 참여자는 정보 수정·배정에서 403, 완료 처리는 통과한다")
    void permissions_differPerActionForParticipant() {
        ProjectDetail created = createProject("권한 경계 확인");
        ProjectDetail started = advanceToInProgress(created);
        var progress = projectLifecycleService.updateProgress(MEMBER_ID,
                new UpdateProgressCommand(created.id(), 100, started.version(), true));

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> projectCommandService.edit(MEMBER_ID, editCommand(
                        created.id(), "권한 경계 확인", ProjectStatus.IN_PROGRESS,
                        progress.version())));
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> assignmentService.assign(MEMBER_ID, new CreateAssignmentCommand(
                        created.id(), NEW_MEMBER_ID, ProjectRole.PARTICIPANT, null, null, 0.1)));

        // 완료 처리는 배정 전원에게 열려 있다 (§4 COMPLETE_REOPEN)
        ProjectDetail completed = projectLifecycleService.complete(
                MEMBER_ID, created.id(), progress.version());
        assertThat(completed.status()).isEqualTo(ProjectStatus.COMPLETED);
    }

    @Test
    @DisplayName("A7-5 — 가시성 밖 프로젝트의 완료 처리·배정은 404 은닉이다")
    void outsideVisibility_isHiddenForWritePaths() {
        ProjectDetail created = createProject("은닉 확인");

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() ->
                        projectLifecycleService.complete(OUTSIDER_ID, created.id(), 0L));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> assignmentService.assign(OUTSIDER_ID,
                        new CreateAssignmentCommand(created.id(), NEW_MEMBER_ID,
                                ProjectRole.PARTICIPANT, null, null, 0.1)));
    }

    @Test
    @DisplayName("A6-5 — PM 배정은 종료할 수 없다 (422, 불변식 보호)")
    void close_managerAssignment_isRejected() {
        ProjectDetail created = createProject("PM 배정 보호");
        long managerAssignmentId = created.assignments().stream()
                .filter(assignment -> assignment.role() == ProjectRole.PM)
                .findFirst()
                .orElseThrow()
                .id();

        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> assignmentService.close(PM_ID, managerAssignmentId))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.INVALID_ROLE));
    }

    @Test
    @DisplayName("A6-1·A6-4 — PM 교체가 배정 역할·managerId를 함께 옮긴다 (미배정이면 배정 생성)")
    void changeManager_movesRoleAndCreatesAssignment() {
        ProjectDetail created = createProject("PM 교체 관통");

        // 미배정 인원(303)을 PM으로 → 배정이 함께 생기고 직전 PM은 참여자가 된다
        ProjectDetail handed = projectLifecycleService.changeManager(
                PM_ID, created.id(), NEW_MEMBER_ID, created.version());

        assertThat(handed.managerId()).isEqualTo(NEW_MEMBER_ID);
        assertThat(handed.assignments())
                .filteredOn(assignment -> assignment.role() == ProjectRole.PM)
                .extracting(AssignmentView::personId)
                .containsExactly(NEW_MEMBER_ID);
        assertThat(handed.assignments())
                .filteredOn(assignment -> assignment.personId() == PM_ID)
                .extracting(AssignmentView::role)
                .containsExactly(ProjectRole.PARTICIPANT);

        // 직전 PM은 이제 참여자라 배정 권한이 없다 (§4-2)
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> projectLifecycleService.changeManager(
                        PM_ID, created.id(), MEMBER_ID, handed.version()));

        // 교체는 UPDATE 이력이다 (A6-1 — STATE_CHANGE는 §5 전이 전용)
        assertThat(auditOf(created.id())).last()
                .satisfies(log -> {
                    assertThat(log.getAction()).isEqualTo(AuditAction.UPDATE);
                    assertThat(log.getAfterState()).contains("\"managerId\":" + NEW_MEMBER_ID);
                });
    }

    @Test
    @DisplayName("A4-1 — 삭제는 목록에서 빠지고 DELETE 이력이 남는다 (생성 권한자도 가능)")
    void delete_removesFromListAndRecordsAudit() {
        ProjectDetail created = createProject("삭제 관통");

        projectCommandService.delete(PM_ID, created.id());

        assertThat(projectQueryService.listVisible(PM_ID, PageRequest.of(0, 50)).getContent())
                .extracting(ProjectSummary::id)
                .doesNotContain(created.id());
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> projectQueryService.getProject(PM_ID, created.id()));
        assertThat(auditOf(created.id())).last()
                .satisfies(log -> {
                    assertThat(log.getAction()).isEqualTo(AuditAction.DELETE);
                    assertThat(log.getAfterState()).contains("\"deleted\":true");
                });
    }

    @Test
    @DisplayName("A4-2 — 참여자는 삭제할 수 없다 (403)")
    void delete_byParticipant_isForbidden() {
        ProjectDetail created = createProject("삭제 권한 확인");

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> projectCommandService.delete(MEMBER_ID, created.id()));
    }

    private ProjectDetail createProject(String name) {
        return projectCommandService.create(PM_ID, new CreateProjectCommand(
                "(주)생애주기",
                name,
                "검색엔진",
                Engagement.REMOTE,
                2.0,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31),
                List.of(
                        new AssignmentSpec(PM_ID, ProjectRole.PM, null, null, 0.5),
                        new AssignmentSpec(MEMBER_ID, ProjectRole.PARTICIPANT, null, null, 0.5))));
    }

    private ProjectDetail advanceToInProgress(ProjectDetail created) {
        ProjectDetail confirmed = projectCommandService.edit(PM_ID, editCommand(
                created.id(), created.name(), ProjectStatus.ORDER_CONFIRMED, created.version()));

        return projectCommandService.edit(PM_ID, editCommand(
                created.id(), created.name(), ProjectStatus.IN_PROGRESS, confirmed.version()));
    }

    private EditProjectCommand editCommand(
            long projectId, String name, ProjectStatus status, long version) {
        return new EditProjectCommand(
                projectId,
                "(주)생애주기",
                name,
                "검색엔진",
                Engagement.REMOTE,
                2.0,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31),
                status,
                version);
    }

    /** 이 프로젝트의 감사 행만 시각순으로 — 조회 뷰(G2-2)는 아직 없어 저장소로 확인한다. */
    private List<AuditLog> auditOf(long projectId) {
        return auditLogRepository.findAll().stream()
                .filter(log -> Long.valueOf(projectId).equals(log.getProjectId()))
                .sorted(Comparator.comparing(AuditLog::getId))
                .toList();
    }
}
