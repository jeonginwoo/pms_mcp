package kr.proten.pms;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.service.dto.PersonSummary;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.PersonService;
import kr.proten.pms.person.service.entity.Grade;
import kr.proten.pms.person.service.entity.PersonFixtures;
import kr.proten.pms.person.service.entity.VisibilityScope;
import kr.proten.pms.project.service.ProjectCommandService;
import kr.proten.pms.project.service.ProjectLifecycleService;
import kr.proten.pms.project.service.ProjectQueryService;
import kr.proten.pms.project.service.dto.AssignmentSpec;
import kr.proten.pms.project.service.dto.AssignmentView;
import kr.proten.pms.project.service.dto.CreateProjectCommand;
import kr.proten.pms.project.service.dto.EditProjectCommand;
import kr.proten.pms.project.service.dto.ProgressUpdateResult;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.dto.ProjectSummary;
import kr.proten.pms.project.service.dto.UpdateProgressCommand;
import kr.proten.pms.project.service.entity.Engagement;
import kr.proten.pms.project.service.entity.ProjectPhase;
import kr.proten.pms.project.service.entity.ProjectRole;
import kr.proten.pms.project.ProjectStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * 사람·프로젝트 관통 통합 검증 — 실물 PostgreSQL.
 *
 * 여기서 검증하는 것은 방언·질의가 실제로 성립하는지다(conventions §8: 방언 타는
 * 검증은 H2로 대체 금지): Flyway 마이그레이션 · 정규화 중복 파생 질의(A1-2) ·
 * IN + 페이징 가시성 질의(A3-1) · @Version 낙관적 락(A2-6). 단위 테스트가 이미
 * 규칙을 검증하므로 여기서는 계층·모듈 배선이 실물에서 이어지는지에 집중한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectFlowIntegrationTest extends PostgresTestBase {
    /**
     * 가시성 단정은 <b>한 페이지에 다 담아 놓고</b> 본다 (2026-08-26 — CI에서만 깨져서 고쳤다).
     *
     * <p>전에는 `PageRequest.of(0, 20)`이었고, 그것은 <b>이 클래스의 프로젝트가 1페이지에
     * 있다</b>는 가정을 숨기고 있었다. 공유 컨테이너에는 다른 통합 테스트가 만든 프로젝트가
     * 함께 쌓이므로 그 가정은 <b>클래스 실행 순서</b>에 달려 있다 — 새 IT 하나가 프로젝트를
     * 다섯 건 더 만들자 이 클래스의 프로젝트가 21번이 되어 COMPANY scope(대표) 목록의
     * 2페이지로 밀렸고, 로컬(NTFS)에서는 통과하는데 CI(ext4)에서만 깨졌다.
     *
     * <p>이 테스트의 주제는 <b>페이징이 아니라 가시성</b>이라 페이지 크기로 답이 갈려서는
     * 안 된다. 페이징 자체는 {@code ProjectApiIntegrationTest}가 본다.
     */
    private static final PageRequest EVERYTHING = PageRequest.of(0, 500);

    private static final long ADMIN_GROUP_ID = 1L;
    private static final long TEAM_LEAD_GROUP_ID = 3L;
    private static final long MEMBER_GROUP_ID = 4L;

    private static final long ADMIN_ID = 1L;
    private static final long SI_LEAD_ID = 102L;
    private static final long SI_MEMBER_ID = 103L;
    private static final long OTHER_DIVISION_MEMBER_ID = 106L;

    @Autowired
    private OrgUnitRepository orgUnitRepository;
    @Autowired
    private GradeRepository gradeRepository;
    @Autowired
    private PermissionGroupRepository permissionGroupRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private PersonService personService;
    @Autowired
    private ProjectCommandService projectCommandService;
    @Autowired
    private ProjectQueryService projectQueryService;
    @Autowired
    private ProjectLifecycleService projectLifecycleService;

    private long visibleProjectId;

    @BeforeAll
    void seedFixture() {
        orgUnitRepository.saveAll(PersonFixtures.orgUnits());
        gradeRepository.saveAll(List.of(
                Grade.of(1L, "수석", 1.5),
                Grade.of(2L, "주임", 1.0)));
        permissionGroupRepository.saveAll(List.of(
                PersonFixtures.group(ADMIN_GROUP_ID, "관리자", VisibilityScope.COMPANY,
                        OrgPermission.CREATE_PROJECT,
                        OrgPermission.MANAGE_CONTRACTS,
                        OrgPermission.MANAGE_ALL_PROJECTS,
                        OrgPermission.MANAGE_ORG),
                PersonFixtures.group(TEAM_LEAD_GROUP_ID, "팀장", VisibilityScope.TEAM,
                        OrgPermission.CREATE_PROJECT),
                PersonFixtures.group(MEMBER_GROUP_ID, "팀원", VisibilityScope.SELF)));
        personRepository.saveAll(List.of(
                PersonFixtures.person(ADMIN_ID, "대표", PersonFixtures.COMPANY_ID, ADMIN_GROUP_ID),
                PersonFixtures.person(SI_LEAD_ID, "에스아이팀장", PersonFixtures.SI_TEAM_ID,
                        TEAM_LEAD_GROUP_ID),
                PersonFixtures.person(SI_MEMBER_ID, "에스아이팀원", PersonFixtures.SI_TEAM_ID,
                        MEMBER_GROUP_ID),
                PersonFixtures.person(OTHER_DIVISION_MEMBER_ID, "타부문원",
                        PersonFixtures.OTHER_DIVISION_ID, MEMBER_GROUP_ID)));

        // SI팀장이 만든 프로젝트 — PM은 SI팀원, 참여자로 타부문원 (타 팀 걸침)
        visibleProjectId = projectCommandService.create(SI_LEAD_ID, new CreateProjectCommand(
                "(주)가온아이",
                "포털 재구축",
                "검색엔진",
                Engagement.REMOTE,
                2.0,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31),
                List.of(
                        new AssignmentSpec(SI_MEMBER_ID, ProjectRole.PM, null, null, 0.5),
                        new AssignmentSpec(OTHER_DIVISION_MEMBER_ID, ProjectRole.PARTICIPANT,
                                null, null, 0.7)))).id();
    }

    @Test
    @DisplayName("A1-1 — 실물 DB에 계약대기로 저장되고 배정 2건이 함께 남는다")
    void create_persistsPendingProjectWithAssignments() {
        ProjectDetail detail = projectQueryService.getProject(SI_LEAD_ID, visibleProjectId);

        assertThat(detail.status()).isEqualTo(ProjectStatus.CONTRACT_PENDING);
        assertThat(detail.managerId()).isEqualTo(SI_MEMBER_ID);
        assertThat(detail.assignments()).hasSize(2);
    }

    @Test
    @DisplayName("A1-2 — 정규화 중복 파생 질의가 실물에서 걸러 낸다")
    void create_duplicateNameAcrossWhitespaceAndCase_isRejectedByDatabase() {
        CreateProjectCommand duplicate = new CreateProjectCommand(
                " (주)가온아이 ",
                "포털   재구축",
                "검색엔진",
                Engagement.ONSITE,
                1.0,
                null,
                null,
                List.of(new AssignmentSpec(SI_MEMBER_ID, ProjectRole.PM, null, null, 0.1)));

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> projectCommandService.create(SI_LEAD_ID, duplicate))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.DUPLICATE_NAME));
    }

    @Test
    @DisplayName("A1-5 — 생성 플래그 없는 팀원 그룹은 403")
    void create_byMemberGroup_isForbidden() {
        CreateProjectCommand command = new CreateProjectCommand(
                "(주)다른고객",
                "신규 구축",
                "검색엔진",
                Engagement.REMOTE,
                1.0,
                null,
                null,
                List.of(new AssignmentSpec(SI_MEMBER_ID, ProjectRole.PM, null, null, 0.1)));

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> projectCommandService.create(SI_MEMBER_ID, command));
    }

    @Test
    @DisplayName("A3-1 — 팀장은 팀 범위, 타부문원은 본인 배정으로 같은 프로젝트를 본다")
    void listVisible_perCaller_appliesUnionOfOrgAndAssignment() {
        var leadPage = projectQueryService.listVisible(SI_LEAD_ID, null, EVERYTHING);
        var outsiderPage =
                projectQueryService.listVisible(OTHER_DIVISION_MEMBER_ID, null, EVERYTHING);
        var adminPage = projectQueryService.listVisible(ADMIN_ID, null, EVERYTHING);

        assertThat(leadPage.getContent()).map(ProjectSummary::id).contains(visibleProjectId);
        assertThat(leadPage.getContent())
                .filteredOn(summary -> summary.id().equals(visibleProjectId))
                .map(ProjectSummary::managerName)
                .containsExactly("에스아이팀원");
        // 타부문원은 조직 가시성 밖이지만 본인 배정이라 보인다 (상위 PRD §4-4 합집합)
        assertThat(outsiderPage.getContent()).map(ProjectSummary::id).contains(visibleProjectId);
        assertThat(adminPage.getContent()).map(ProjectSummary::id).contains(visibleProjectId);
    }

    @Test
    @DisplayName("A3-1 ?phase= — 필터는 가시성을 넓히지 않고 두 탭이 무필터 목록을 정확히 나눈다")
    void listVisible_phaseFilter_partitionsWithoutWideningVisibility() {
        // 특정 프로젝트의 상태에 기대지 않는다 — 이 클래스는 PER_CLASS라 다른 테스트가
        // 상태를 옮길 수 있다. 성립해야 하는 것은 그것과 무관한 두 성질이다.
        List<Long> all = idsOf(OTHER_DIVISION_MEMBER_ID, null);
        List<Long> sales = idsOf(OTHER_DIVISION_MEMBER_ID, ProjectPhase.SALES);
        List<Long> solution = idsOf(OTHER_DIVISION_MEMBER_ID, ProjectPhase.SOLUTION);

        // ① 필터를 걸었다고 범위 밖 프로젝트가 따라 들어오지 않는다 — 판정이 먼저다
        assertThat(sales).isSubsetOf(all);
        assertThat(solution).isSubsetOf(all);
        // ② 이 픽스처에는 유지보수중이 없으므로 두 탭이 무필터를 정확히 나눈다.
        // 한 프로젝트가 두 탭에 걸치면 concat에 중복이 생겨 이 단정이 깨진다 —
        // 겹침 검사를 따로 두지 않는 이유이고, 한쪽 탭이 비어도 성립한다.
        assertThat(all).containsExactlyInAnyOrderElementsOf(
                Stream.concat(sales.stream(), solution.stream()).toList());
        assertThat(all).contains(visibleProjectId);

        assertThat(projectQueryService
                .listVisible(OTHER_DIVISION_MEMBER_ID, ProjectPhase.SALES, EVERYTHING)
                .getContent())
                .allSatisfy(summary -> assertThat(summary.phase()).isEqualTo(ProjectPhase.SALES));
    }

    private List<Long> idsOf(long callerPersonId, ProjectPhase phase) {
        return projectQueryService.listVisible(callerPersonId, phase, EVERYTHING)
                .getContent().stream()
                .map(ProjectSummary::id)
                .toList();
    }

    @Test
    @DisplayName("A3-2 — 없는 프로젝트와 가시성 밖 프로젝트는 같은 404")
    void getProject_absentOrInvisible_throwsSameNotFound() {
        long invisibleProjectId = projectCommandService.create(ADMIN_ID, new CreateProjectCommand(
                "(주)비공개고객",
                "타부문 전용 과제",
                "검색엔진",
                Engagement.REMOTE,
                1.0,
                null,
                null,
                List.of(new AssignmentSpec(OTHER_DIVISION_MEMBER_ID, ProjectRole.PM,
                        null, null, 0.3)))).id();

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> projectQueryService.getProject(SI_MEMBER_ID, 999_999L));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> projectQueryService.getProject(SI_MEMBER_ID, invisibleProjectId));
    }

    @Test
    @DisplayName("A3-3 — 상세의 배정 레코드는 타부문 인원 이름까지 채워진다")
    void getProject_detail_resolvesPersonNamesAcrossModule() {
        ProjectDetail detail =
                projectQueryService.getProject(OTHER_DIVISION_MEMBER_ID, visibleProjectId);

        assertThat(detail.assignments()).map(AssignmentView::personName)
                .containsExactlyInAnyOrder("에스아이팀원", "타부문원");
    }

    @Test
    @DisplayName("A2-1·A2-2·A2-6 — 2단계 확인과 낙관적 락이 실물 version으로 동작한다")
    void updateProgress_twoStepAndOptimisticLock() {
        long projectId = projectCommandService.create(ADMIN_ID, new CreateProjectCommand(
                "(주)락테스트",
                "진척률 과제",
                "검색엔진",
                Engagement.REMOTE,
                1.0,
                null,
                null,
                List.of(new AssignmentSpec(SI_MEMBER_ID, ProjectRole.PM, null, null, 0.3)))).id();
        // 진척률은 진행중에서만 수정한다(2026-08-22 결정) — 순방향 두 칸을 먼저 밟는다
        long version = advanceToInProgress(projectId);

        // 확인 전 — 요약만, DB 미변경
        ProgressUpdateResult summary = projectLifecycleService.updateProgress(
                SI_MEMBER_ID, new UpdateProgressCommand(projectId, 40, version, false));
        assertThat(summary.committed()).isFalse();
        assertThat(projectQueryService.getProject(SI_MEMBER_ID, projectId).progress()).isZero();

        // 확인 후 — 커밋 + version 증가
        ProgressUpdateResult committed = projectLifecycleService.updateProgress(
                SI_MEMBER_ID, new UpdateProgressCommand(projectId, 40, version, true));
        assertThat(committed.committed()).isTrue();
        assertThat(committed.version()).isGreaterThan(version);

        // 지나간 version으로 재시도 — 409
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> projectLifecycleService.updateProgress(
                        SI_MEMBER_ID, new UpdateProgressCommand(projectId, 60, version, true)))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.STALE_VERSION));
    }

    @Test
    @DisplayName("A2-4 — 미배정 화자는 가시성 안이면 403, 밖이면 404")
    void updateProgress_unassignedCaller_distinguishesForbiddenAndHidden() {
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> projectLifecycleService.updateProgress(SI_LEAD_ID,
                        new UpdateProgressCommand(visibleProjectId, 50, 0L, true)));

        long hiddenProjectId = projectCommandService.create(ADMIN_ID, new CreateProjectCommand(
                "(주)은닉고객",
                "은닉 과제",
                "검색엔진",
                Engagement.REMOTE,
                1.0,
                null,
                null,
                List.of(new AssignmentSpec(OTHER_DIVISION_MEMBER_ID, ProjectRole.PM,
                        null, null, 0.3)))).id();

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> projectLifecycleService.updateProgress(SI_MEMBER_ID,
                        new UpdateProgressCommand(hiddenProjectId, 50, 0L, true)));
    }

    /** 계약대기 → 수주확정 → 진행중 — 진척률 경로의 전제를 만든다. 최신 version을 준다. */
    private long advanceToInProgress(long projectId) {
        ProjectDetail confirmed = projectCommandService.edit(ADMIN_ID,
                editCommand(projectId, ProjectStatus.ORDER_CONFIRMED,
                        projectQueryService.getProject(ADMIN_ID, projectId).version()));

        return projectCommandService.edit(ADMIN_ID,
                editCommand(projectId, ProjectStatus.IN_PROGRESS, confirmed.version())).version();
    }

    private EditProjectCommand editCommand(long projectId, ProjectStatus status, long version) {
        ProjectDetail current = projectQueryService.getProject(ADMIN_ID, projectId);

        return new EditProjectCommand(projectId, current.client(), current.name(),
                current.solution(), current.engagement(), current.contractMm(),
                current.startDate(), current.endDate(), status, version);
    }

    @Test
    @DisplayName("인력 조회 — 팀장은 팀 subtree만, 관리자는 전사가 보인다")
    void listVisiblePeople_perCallerScope() {
        List<PersonSummary> leadView = personService.listVisible(SI_LEAD_ID);
        List<PersonSummary> adminView = personService.listVisible(ADMIN_ID);

        assertThat(leadView).map(PersonSummary::name)
                .containsExactlyInAnyOrder("에스아이팀장", "에스아이팀원");
        assertThat(adminView).map(PersonSummary::name).contains("타부문원");
        assertThat(leadView.getFirst().orgUnit()).isEqualTo("SI팀");
    }
}
