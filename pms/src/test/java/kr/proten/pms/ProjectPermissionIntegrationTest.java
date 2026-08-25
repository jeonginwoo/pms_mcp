package kr.proten.pms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;
import java.util.List;
import kr.proten.pms.audit.AuditAction;
import kr.proten.pms.audit.repository.AuditLogRepository;
import kr.proten.pms.audit.service.entity.AuditLog;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.StaleVersionException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.common.exception.ValidationException;
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
import kr.proten.pms.project.service.ProjectPermissionService;
import kr.proten.pms.project.service.dto.AssignmentSpec;
import kr.proten.pms.project.service.dto.CreateAssignmentCommand;
import kr.proten.pms.project.service.dto.CreateProjectCommand;
import kr.proten.pms.project.service.dto.EditProjectCommand;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.dto.ProjectPermissionMatrix;
import kr.proten.pms.project.service.dto.UpdateProgressCommand;
import kr.proten.pms.project.service.dto.UpdateProjectPermissionsCommand;
import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.project.service.entity.Engagement;
import kr.proten.pms.project.service.entity.ProjectAction;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 프로젝트별 권한 커스텀 관통 (US-A8) — 실물 PostgreSQL.
 *
 * <p>여기서만 확인할 수 있는 것: ①저장된 override가 <b>다른 유스케이스의 판정</b>에
 * 실제로 닿는지(A8-5·A8-6 — 단위 테스트는 판정기 하나만 본다) ②"기본값과 같은 값은
 * 저장하지 않는다"가 <b>실물 행 수</b>로 성립하는지 ③감사가 저장 한 번에 한 행인지
 * ④version 강제 증가가 두 번째 저장을 실제로 막는지.
 *
 * <p>다른 클래스와 컨테이너·컨텍스트를 공유하므로 식별자는 <b>500번대</b>에 둔다
 * (100·200·300·400·600~1000은 이미 다른 통합 테스트의 것이다 — 2026-08-25에 6xx가
 * 겹쳐 낙관적 락 실패로 터진 적이 있어 블록은 실측하고 고른다).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectPermissionIntegrationTest extends PostgresTestBase {
    private static final long ADMIN_GROUP_ID = 51L;
    private static final long MEMBER_GROUP_ID = 52L;
    private static final long OUTSIDER_GROUP_ID = 53L;

    private static final long PM_ID = 501L;
    private static final long LEAD_ID = 502L;
    private static final long MEMBER_ID = 503L;
    /** 이 프로젝트들이 보이지 않는 화자 — 404 은닉의 증명에 쓴다(SELF 가시성) */
    private static final long OUTSIDER_ID = 504L;

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
    private ProjectLifecycleService projectLifecycleService;
    @Autowired
    private AssignmentService assignmentService;
    @Autowired
    private ProjectPermissionService projectPermissionService;

    @BeforeAll
    void seedFixture() {
        orgUnitRepository.saveAll(PersonFixtures.orgUnits());
        gradeRepository.save(Grade.of(1L, "수석", 1.5));
        permissionGroupRepository.saveAll(List.of(
                PersonFixtures.group(ADMIN_GROUP_ID, "권한관리자", VisibilityScope.COMPANY,
                        OrgPermission.CREATE_PROJECT),
                PersonFixtures.group(MEMBER_GROUP_ID, "권한팀원", VisibilityScope.COMPANY),
                PersonFixtures.group(OUTSIDER_GROUP_ID, "권한외부", VisibilityScope.SELF)));
        personRepository.saveAll(List.of(
                PersonFixtures.person(PM_ID, "권한피엠", PersonFixtures.OTHER_DIVISION_ID,
                        ADMIN_GROUP_ID),
                PersonFixtures.person(LEAD_ID, "권한피엘", PersonFixtures.OTHER_DIVISION_ID,
                        MEMBER_GROUP_ID),
                PersonFixtures.person(MEMBER_ID, "권한참여자", PersonFixtures.OTHER_DIVISION_ID,
                        MEMBER_GROUP_ID),
                PersonFixtures.person(OUTSIDER_ID, "권한외부인", PersonFixtures.CS_TEAM_ID,
                        OUTSIDER_GROUP_ID)));
    }

    @Test
    @DisplayName("A8-1 — 매트릭스는 고정 칸까지 전부 담고 기본값은 §4-2 그대로다")
    void matrix_carriesEveryCellIncludingFixedOnes() {
        ProjectDetail project = createProject("권한 매트릭스 조회");

        ProjectPermissionMatrix matrix = projectPermissionService.getMatrix(PM_ID, project.id());

        // 3역할 × 5기능 = 15칸 — 화면이 잠금 표시를 그리려면 고정 칸도 와야 한다
        assertThat(matrix.cells()).hasSize(15);
        assertThat(editableCells(matrix)).hasSize(8);

        // 기본값(§4-2): 배정은 PM만 · 진척률은 배정 전원
        assertThat(cell(matrix, ProjectRole.PL, ProjectAction.ASSIGN).allowed()).isFalse();
        assertThat(cell(matrix, ProjectRole.PARTICIPANT, ProjectAction.PROGRESS).allowed()).isTrue();
        // 고정 칸: PM 열 전체와 이관 행
        assertThat(cell(matrix, ProjectRole.PM, ProjectAction.PROGRESS).editable()).isFalse();
        assertThat(cell(matrix, ProjectRole.PL, ProjectAction.HANDOVER).editable()).isFalse();
        assertThat(matrix.cells()).allMatch(c -> !c.overridden());
    }

    @Test
    @DisplayName("A8-1·A8-3 — 조회는 가시성 범위 전원이고 저장은 PM만이다")
    void matrix_readableByEveryone_writableByManagerOnly() {
        ProjectDetail project = createProject("권한 읽기 쓰기");
        assign(project.id(), MEMBER_ID, ProjectRole.PARTICIPANT);

        // 참여자도 읽는다 — 화면이 잠금 표시를 그려야 하기 때문이다
        assertThat(projectPermissionService.getMatrix(MEMBER_ID, project.id()).cells()).hasSize(15);

        assertThatExceptionOfType(ForbiddenException.class).isThrownBy(() ->
                projectPermissionService.updateOverrides(MEMBER_ID, project.id(),
                        turnOff(ProjectRole.PARTICIPANT, ProjectAction.PROGRESS,
                                project.version())));
    }

    @Test
    @DisplayName("A8-5 — PROGRESS를 끄면 그 프로젝트의 참여자가 실제로 403을 받는다")
    void turningOffProgress_reachesTheProgressUseCase() {
        ProjectDetail project = inProgressProject("권한 진척률 잠금");
        assign(project.id(), MEMBER_ID, ProjectRole.PARTICIPANT);

        // 끄기 전에는 참여자가 쓸 수 있다 — 이 단정이 없으면 뒤의 403이 무엇 때문인지 모른다
        var before = projectLifecycleService.updateProgress(MEMBER_ID,
                new UpdateProgressCommand(project.id(), 30, currentVersion(project.id()), true));
        assertThat(before.currentProgress()).isEqualTo(30);

        projectPermissionService.updateOverrides(PM_ID, project.id(),
                turnOff(ProjectRole.PARTICIPANT, ProjectAction.PROGRESS,
                        currentVersion(project.id())));

        long version = currentVersion(project.id());
        assertThatExceptionOfType(ForbiddenException.class).isThrownBy(() ->
                projectLifecycleService.updateProgress(MEMBER_ID,
                        new UpdateProgressCommand(project.id(), 40, version, true)));

        // PM은 같은 프로젝트에서 그대로 쓴다 — 끈 것은 참여자 칸 하나다
        assertThat(projectLifecycleService.updateProgress(PM_ID,
                new UpdateProgressCommand(project.id(), 40, version, true)).currentProgress())
                .isEqualTo(40);
    }

    @Test
    @DisplayName("A8-6 — ASSIGN을 PL로 확장하면 그 프로젝트의 PL이 배정할 수 있다")
    void extendingAssign_reachesTheAssignmentUseCase() {
        ProjectDetail project = createProject("권한 배정 확장");
        assign(project.id(), LEAD_ID, ProjectRole.PL);

        // 기본값에서는 PL이 배정하지 못한다(B1-4)
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> assign(project.id(), MEMBER_ID, ProjectRole.PARTICIPANT, LEAD_ID));

        projectPermissionService.updateOverrides(PM_ID, project.id(),
                turnOn(ProjectRole.PL, ProjectAction.ASSIGN, currentVersion(project.id())));

        assign(project.id(), MEMBER_ID, ProjectRole.PARTICIPANT, LEAD_ID);
        assertThat(projectPermissionService.getMatrix(PM_ID, project.id()))
                .extracting(m -> cell(m, ProjectRole.PL, ProjectAction.ASSIGN).allowed())
                .isEqualTo(true);
    }

    @Test
    @DisplayName("A8-2 — 기본값과 같은 값은 저장되지 않고, 빈 목록은 전체 기본값 복원이다")
    void defaultsAreNotStored_andEmptyRestoresAll() {
        ProjectDetail project = createProject("권한 기본값 복원");

        // 기본값과 같은 값(참여자 진척률 = 허용)을 보내도 override로 남지 않는다
        ProjectPermissionMatrix sameAsDefault = projectPermissionService.updateOverrides(
                PM_ID, project.id(),
                turnOn(ProjectRole.PARTICIPANT, ProjectAction.PROGRESS, project.version()));
        assertThat(sameAsDefault.cells()).allMatch(c -> !c.overridden());

        // 기본값과 다른 값은 남는다
        ProjectPermissionMatrix off = projectPermissionService.updateOverrides(
                PM_ID, project.id(),
                turnOff(ProjectRole.PARTICIPANT, ProjectAction.PROGRESS,
                        currentVersion(project.id())));
        assertThat(cell(off, ProjectRole.PARTICIPANT, ProjectAction.PROGRESS).overridden()).isTrue();
        assertThat(cell(off, ProjectRole.PARTICIPANT, ProjectAction.PROGRESS).allowed()).isFalse();

        // 빈 목록 = 전체 복원 (별도 API 없이 이것 하나로 끝난다)
        ProjectPermissionMatrix restored = projectPermissionService.updateOverrides(
                PM_ID, project.id(),
                new UpdateProjectPermissionsCommand(List.of(), currentVersion(project.id())));
        assertThat(restored.cells()).allMatch(c -> !c.overridden());
        assertThat(cell(restored, ProjectRole.PARTICIPANT, ProjectAction.PROGRESS).allowed())
                .isTrue();
    }

    @Test
    @DisplayName("A8-4 — 고정 칸이 섞이면 422이고 같은 요청의 나머지도 저장되지 않는다")
    void fixedCellInRequest_rejectsWholeRequest() {
        ProjectDetail project = createProject("권한 고정 칸");

        UpdateProjectPermissionsCommand mixed = new UpdateProjectPermissionsCommand(List.of(
                // 이 칸은 정상이다 — 그런데도 저장되면 안 된다("아무것도 안 바뀜")
                new UpdateProjectPermissionsCommand.Override(
                        ProjectRole.PL, ProjectAction.PROGRESS, false),
                new UpdateProjectPermissionsCommand.Override(
                        ProjectRole.PM, ProjectAction.PROGRESS, false)),
                project.version());

        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> projectPermissionService.updateOverrides(
                        PM_ID, project.id(), mixed))
                .matches(e -> e.code() == ErrorCode.IMMUTABLE_PERMISSION);

        assertThat(projectPermissionService.getMatrix(PM_ID, project.id()).cells())
                .allMatch(c -> !c.overridden());
    }

    @Test
    @DisplayName("A8-4 — 이관 행도 고정이다 (유효 action은 §4의 4종)")
    void handoverRow_isFixedToo() {
        ProjectDetail project = createProject("권한 이관 행");

        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> projectPermissionService.updateOverrides(PM_ID, project.id(),
                        turnOn(ProjectRole.PL, ProjectAction.HANDOVER, project.version())))
                .matches(e -> e.code() == ErrorCode.IMMUTABLE_PERMISSION);
    }

    @Test
    @DisplayName("A8-7 — 응답의 version으로 곧바로 다시 저장할 수 있다 (§7 왕복)")
    void returnedVersionIsUsableForTheNextSave() {
        ProjectDetail project = createProject("권한 version 왕복");

        ProjectPermissionMatrix first = projectPermissionService.updateOverrides(
                PM_ID, project.id(),
                turnOff(ProjectRole.PL, ProjectAction.EDIT_INFO, project.version()));

        // 증가 **후** 값이어야 한다 — 증가 전 값을 돌려주면 그것으로 재저장할 때
        // 위반한 적 없는 락에 걸려 409를 받는다(2026-08-24에 세 라우트에서 겪은 결함)
        assertThat(first.version()).isEqualTo(project.version() + 1);

        /*
         * 두 번째 저장은 **기본값과 다른** 칸을 골라야 한다: 참여자의 EDIT_INFO는
         * 기본값이 이미 false라(§4-2 정보 수정 = PM·PL) 끄는 것이 기본값과 같고,
         * 그러면 A8-2대로 행이 남지 않는다. 진척률은 기본값이 true라 끄면 남는다.
         */
        ProjectPermissionMatrix second = projectPermissionService.updateOverrides(
                PM_ID, project.id(),
                turnOff(ProjectRole.PARTICIPANT, ProjectAction.PROGRESS, first.version()));
        assertThat(second.version()).isEqualTo(first.version() + 1);
        assertThat(cell(second, ProjectRole.PARTICIPANT, ProjectAction.PROGRESS).overridden())
                .isTrue();
        // 전체 교체라 첫 저장의 칸은 기본값으로 돌아갔다
        assertThat(cell(second, ProjectRole.PL, ProjectAction.EDIT_INFO).overridden()).isFalse();
    }

    @Test
    @DisplayName("A8-7 — 낡은 version의 두 번째 저장은 409이고 아무것도 바뀌지 않는다")
    void secondSaveWithTheSameVersion_conflicts() {
        ProjectDetail project = createProject("권한 낙관적 락");
        long version = project.version();

        projectPermissionService.updateOverrides(PM_ID, project.id(),
                turnOff(ProjectRole.PL, ProjectAction.EDIT_INFO, version));

        // 매트릭스는 별도 표에 저장되므로 version을 스스로 올리지 않으면 이것이 통과한다
        assertThatExceptionOfType(StaleVersionException.class)
                .isThrownBy(() -> projectPermissionService.updateOverrides(PM_ID, project.id(),
                        turnOff(ProjectRole.PARTICIPANT, ProjectAction.EDIT_INFO, version)));

        // 거절된 요청의 칸은 남지 않았다
        assertThat(cell(projectPermissionService.getMatrix(PM_ID, project.id()),
                ProjectRole.PARTICIPANT, ProjectAction.EDIT_INFO).overridden()).isFalse();
    }

    @Test
    @DisplayName("A8-2 — 같은 칸이 두 번 담긴 요청은 500이 아니라 §7 봉투 400이다")
    void duplicateCellsInRequest_isValidationError() {
        ProjectDetail project = createProject("권한 중복 칸");

        UpdateProjectPermissionsCommand duplicated = new UpdateProjectPermissionsCommand(List.of(
                new UpdateProjectPermissionsCommand.Override(
                        ProjectRole.PL, ProjectAction.ASSIGN, true),
                new UpdateProjectPermissionsCommand.Override(
                        ProjectRole.PL, ProjectAction.ASSIGN, true)),
                project.version());

        // 유니크 제약에 부딪히면 DataIntegrityViolation → catch-all 500이 된다.
        // 호출자가 고칠 수 있는 요청 오류가 서버 장애로 보고되면 안 된다(2026-08-22 선례)
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> projectPermissionService.updateOverrides(
                        PM_ID, project.id(), duplicated));
    }

    @Test
    @DisplayName("A8-2 — 감사는 저장 한 번에 한 행이고 바뀐 칸만 담는다")
    void auditIsOneRowPerSave() {
        ProjectDetail project = createProject("권한 감사");
        long before = auditLogRepository.count();

        // 한 번에 두 칸 — 사람이 읽는 사건의 단위는 "저장"이다
        projectPermissionService.updateOverrides(PM_ID, project.id(),
                new UpdateProjectPermissionsCommand(List.of(
                        new UpdateProjectPermissionsCommand.Override(
                                ProjectRole.PL, ProjectAction.PROGRESS, false),
                        new UpdateProjectPermissionsCommand.Override(
                                ProjectRole.PARTICIPANT, ProjectAction.PROGRESS, false)),
                        project.version()));

        assertThat(auditLogRepository.count()).isEqualTo(before + 1);

        AuditLog row = auditLogRepository.findAll().stream()
                .filter(log -> project.id().equals(log.getProjectId()))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(row.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(row.getActorId()).isEqualTo(PM_ID);
        assertThat(row.getAfterState())
                .contains("\"PL.PROGRESS\":false")
                .contains("\"PARTICIPANT.PROGRESS\":false");
        // 안 바뀐 칸은 diff에 없다 — 스냅샷 전량을 찍으면 무엇이 바뀌었는지 읽을 수 없다
        assertThat(row.getAfterState()).doesNotContain("PL.EDIT_INFO");
    }

    @Test
    @DisplayName("A8-2 — 바꾼 것이 없으면 감사 행도 없다")
    void noChange_noAuditRow() {
        ProjectDetail project = createProject("권한 무변화");
        long before = auditLogRepository.count();

        projectPermissionService.updateOverrides(PM_ID, project.id(),
                new UpdateProjectPermissionsCommand(List.of(), project.version()));

        assertThat(auditLogRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("A3-2 — 부재와 가시성 밖이 **같은** 404다 (은닉)")
    void absentAndInvisibleProjectsAreTheSame404() {
        ProjectDetail project = createProject("권한 은닉");

        // 부재
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> projectPermissionService.getMatrix(PM_ID, 9_999_999L));

        // 실재하지만 가시성 밖 — 이쪽이 은닉의 본체다. 부재만 보면 "숨긴다"는 증명이 없다
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> projectPermissionService.getMatrix(OUTSIDER_ID, project.id()));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> projectPermissionService.updateOverrides(
                        OUTSIDER_ID, project.id(),
                        turnOff(ProjectRole.PL, ProjectAction.EDIT_INFO, project.version())));
    }

    @Test
    @DisplayName("A8-3 — PL도 저장은 403이다 (조정은 PM만)")
    void leadCannotSavePermissions() {
        ProjectDetail project = createProject("권한 PL 거절");
        assign(project.id(), LEAD_ID, ProjectRole.PL);

        // PL은 읽을 수 있다
        assertThat(projectPermissionService.getMatrix(LEAD_ID, project.id()).cells()).hasSize(15);

        assertThatExceptionOfType(ForbiddenException.class).isThrownBy(() ->
                projectPermissionService.updateOverrides(LEAD_ID, project.id(),
                        turnOff(ProjectRole.PARTICIPANT, ProjectAction.PROGRESS,
                                project.version())));
    }

    private ProjectPermissionMatrix.Cell cell(
            ProjectPermissionMatrix matrix, ProjectRole role, ProjectAction action) {
        return matrix.cells().stream()
                .filter(c -> c.role().equals(role.name()) && c.action().equals(action.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("칸이 없다: " + role + "." + action));
    }

    private List<ProjectPermissionMatrix.Cell> editableCells(ProjectPermissionMatrix matrix) {
        return matrix.cells().stream().filter(ProjectPermissionMatrix.Cell::editable).toList();
    }

    private UpdateProjectPermissionsCommand turnOff(
            ProjectRole role, ProjectAction action, long version) {
        return new UpdateProjectPermissionsCommand(List.of(
                new UpdateProjectPermissionsCommand.Override(role, action, false)), version);
    }

    private UpdateProjectPermissionsCommand turnOn(
            ProjectRole role, ProjectAction action, long version) {
        return new UpdateProjectPermissionsCommand(List.of(
                new UpdateProjectPermissionsCommand.Override(role, action, true)), version);
    }

    private long currentVersion(long projectId) {
        return projectPermissionService.getMatrix(PM_ID, projectId).version();
    }

    private ProjectDetail createProject(String name) {
        return projectCommandService.create(PM_ID, new CreateProjectCommand(
                "(주)권한",
                name,
                "검색엔진",
                Engagement.REMOTE,
                2.0,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31),
                List.of(new AssignmentSpec(PM_ID, ProjectRole.PM, null, null, 0.5))));
    }

    /** 진행중까지 올린 프로젝트 — 진척률 쓰기는 진행중에서만 가능하다(A2-9) */
    private ProjectDetail inProgressProject(String name) {
        ProjectDetail created = createProject(name);
        ProjectDetail confirmed = advance(created, ProjectStatus.ORDER_CONFIRMED);

        return advance(confirmed, ProjectStatus.IN_PROGRESS);
    }

    private ProjectDetail advance(ProjectDetail project, ProjectStatus target) {
        return projectCommandService.edit(PM_ID, new EditProjectCommand(
                project.id(),
                project.client(),
                project.name(),
                project.solution(),
                project.engagement(),
                project.contractMm(),
                project.startDate(),
                project.endDate(),
                target,
                project.version()));
    }

    private void assign(long projectId, long personId, ProjectRole role) {
        assign(projectId, personId, role, PM_ID);
    }

    private void assign(long projectId, long personId, ProjectRole role, long callerId) {
        assignmentService.assign(callerId, new CreateAssignmentCommand(
                projectId, personId, role, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31), 1.0));
    }
}
