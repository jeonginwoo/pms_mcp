package kr.proten.pms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;
import java.util.List;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.person.service.dto.OrgPermission;
import kr.proten.pms.person.service.dto.PersonRef;
import kr.proten.pms.person.service.entity.Grade;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.service.entity.PersonFixtures;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.entity.VisibilityScope;
import kr.proten.pms.person.service.PersonQueryService;
import kr.proten.pms.project.service.entity.Engagement;
import kr.proten.pms.project.service.entity.ProjectRole;
import kr.proten.pms.project.service.entity.ProjectStatus;
import kr.proten.pms.project.service.dto.AssignmentSpec;
import kr.proten.pms.project.service.dto.AssignmentView;
import kr.proten.pms.project.service.dto.CreateProjectCommand;
import kr.proten.pms.project.service.dto.ProgressUpdateResult;
import kr.proten.pms.project.service.ProgressUpdateService;
import kr.proten.pms.project.service.ProjectCommandService;
import kr.proten.pms.project.service.ProjectEditService;
import kr.proten.pms.project.service.dto.EditProjectCommand;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.ProjectQueryService;
import kr.proten.pms.project.service.dto.ProjectSummary;
import kr.proten.pms.project.service.dto.UpdateProgressCommand;
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
    private PersonQueryService personQueryService;
    @Autowired
    private ProjectCommandService projectCommandService;
    @Autowired
    private ProjectQueryService projectQueryService;
    @Autowired
    private ProgressUpdateService progressUpdateService;
    @Autowired
    private ProjectEditService projectEditService;

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
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo("DUPLICATE_NAME"));
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
        var leadPage = projectQueryService.listVisible(SI_LEAD_ID, PageRequest.of(0, 20));
        var outsiderPage =
                projectQueryService.listVisible(OTHER_DIVISION_MEMBER_ID, PageRequest.of(0, 20));
        var adminPage = projectQueryService.listVisible(ADMIN_ID, PageRequest.of(0, 20));

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
        ProgressUpdateResult summary = progressUpdateService.update(
                SI_MEMBER_ID, new UpdateProgressCommand(projectId, 40, version, false));
        assertThat(summary.committed()).isFalse();
        assertThat(projectQueryService.getProject(SI_MEMBER_ID, projectId).progress()).isZero();

        // 확인 후 — 커밋 + version 증가
        ProgressUpdateResult committed = progressUpdateService.update(
                SI_MEMBER_ID, new UpdateProgressCommand(projectId, 40, version, true));
        assertThat(committed.committed()).isTrue();
        assertThat(committed.version()).isGreaterThan(version);

        // 지나간 version으로 재시도 — 409
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> progressUpdateService.update(
                        SI_MEMBER_ID, new UpdateProgressCommand(projectId, 60, version, true)))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo("STALE_VERSION"));
    }

    @Test
    @DisplayName("A2-4 — 미배정 화자는 가시성 안이면 403, 밖이면 404")
    void updateProgress_unassignedCaller_distinguishesForbiddenAndHidden() {
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> progressUpdateService.update(SI_LEAD_ID,
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
                .isThrownBy(() -> progressUpdateService.update(SI_MEMBER_ID,
                        new UpdateProgressCommand(hiddenProjectId, 50, 0L, true)));
    }

    /** 계약대기 → 수주확정 → 진행중 — 진척률 경로의 전제를 만든다. 최신 version을 준다. */
    private long advanceToInProgress(long projectId) {
        ProjectDetail confirmed = projectEditService.edit(ADMIN_ID,
                editCommand(projectId, ProjectStatus.ORDER_CONFIRMED,
                        projectQueryService.getProject(ADMIN_ID, projectId).version()));

        return projectEditService.edit(ADMIN_ID,
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
        List<PersonRef> leadView = personQueryService.listVisible(SI_LEAD_ID);
        List<PersonRef> adminView = personQueryService.listVisible(ADMIN_ID);

        assertThat(leadView).map(PersonRef::name)
                .containsExactlyInAnyOrder("에스아이팀장", "에스아이팀원");
        assertThat(adminView).map(PersonRef::name).contains("타부문원");
        assertThat(leadView.getFirst().orgUnit()).isEqualTo("SI팀");
    }
}
