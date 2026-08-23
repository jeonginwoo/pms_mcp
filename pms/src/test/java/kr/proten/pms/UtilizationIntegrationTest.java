package kr.proten.pms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.entity.Grade;
import kr.proten.pms.person.service.entity.OrgUnit;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.service.entity.PersonFixtures;
import kr.proten.pms.person.service.entity.VisibilityScope;
import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.project.service.AssignmentService;
import kr.proten.pms.project.service.ProjectCommandService;
import kr.proten.pms.project.service.ProjectQueryService;
import kr.proten.pms.project.service.dto.AssignmentSpec;
import kr.proten.pms.project.service.dto.AssignmentView;
import kr.proten.pms.project.service.dto.CreateProjectCommand;
import kr.proten.pms.project.service.dto.EditProjectCommand;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.dto.UpdateAssignmentCommand;
import kr.proten.pms.project.service.entity.Engagement;
import kr.proten.pms.project.service.entity.ProjectRole;
import kr.proten.pms.resource.repository.CapacityRepository;
import kr.proten.pms.resource.service.UtilizationQueryService;
import kr.proten.pms.resource.service.dto.UtilizationQuery;
import kr.proten.pms.resource.service.dto.UtilizationView;
import kr.proten.pms.resource.service.entity.Capacity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 가동률 관통 통합 검증 — 실물 PostgreSQL (EPIC C).
 *
 * <p>단위 테스트가 산식과 모집단 규칙을 이미 고정하므로 여기서 보는 것은 <b>모듈 배선이
 * 실물에서 이어지는지</b>다: resource → project(배정 행) → person(속성·가시성) 세 모듈을
 * 건너 한 응답이 만들어지는지, 그리고 배정 합산이 실 질의로 성립하는지.
 *
 * <p><b>C1-4가 여기 있는 이유</b>: 조회 시점 계산이라 재계산 이벤트가 없다는 것이 설계인데
 * (캐시 미도입 2026-08-06), 그 사실은 코드에 보이지 않는다 — 배정을 바꾼 직후 조회가 새 값을
 * 내는 것으로만 증명된다.
 *
 * <p>공유 컨테이너를 쓰므로 <b>전용 조직·인원 id 블록</b>(4xx)을 쓰고 집계는 자기 조직
 * subtree로만 좁힌다 — 다른 통합 테스트가 넣은 인원이 전사 집계에 섞이기 때문이다.
 * 테스트마다 <b>기준 월을 달리</b> 두는 것도 같은 이유다(한 배정을 고치면 그 배정이 걸친
 * 모든 달이 바뀐다).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UtilizationIntegrationTest extends PostgresTestBase {
    private static final long DIVISION_ID = 41L;
    private static final long TEAM_ID = 42L;
    private static final long OTHER_TEAM_ID = 43L;

    private static final long ADMIN_GROUP_ID = 41L;
    private static final long MEMBER_GROUP_ID = 42L;

    private static final long ADMIN_ID = 401L;
    private static final long SUBJECT_ID = 402L;
    private static final long CHANGING_ID = 403L;
    private static final long NON_BILLABLE_ID = 404L;

    /** 시드와 같은 계수 — 수석 1.5 (부록 B). */
    private static final double SENIOR_COEFF = 1.5;

    @Autowired
    private OrgUnitRepository orgUnitRepository;
    @Autowired
    private GradeRepository gradeRepository;
    @Autowired
    private PermissionGroupRepository permissionGroupRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private CapacityRepository capacityRepository;
    @Autowired
    private ProjectCommandService projectCommandService;
    @Autowired
    private ProjectQueryService projectQueryService;
    @Autowired
    private AssignmentService assignmentService;
    @Autowired
    private UtilizationQueryService utilizationQueryService;

    private long changingProjectId;

    @BeforeAll
    void seedFixture() {
        orgUnitRepository.saveAll(PersonFixtures.orgUnits());
        orgUnitRepository.saveAll(List.of(
                OrgUnit.of(DIVISION_ID, PersonFixtures.COMPANY_ID, "가동률사업부"),
                OrgUnit.of(TEAM_ID, DIVISION_ID, "가동률팀"),
                OrgUnit.of(OTHER_TEAM_ID, DIVISION_ID, "가동률2팀")));
        gradeRepository.save(Grade.of(1L, "수석", SENIOR_COEFF));
        permissionGroupRepository.saveAll(List.of(
                PersonFixtures.group(ADMIN_GROUP_ID, "가동률관리자", VisibilityScope.COMPANY,
                        OrgPermission.CREATE_PROJECT, OrgPermission.MANAGE_ALL_PROJECTS),
                PersonFixtures.group(MEMBER_GROUP_ID, "가동률팀원", VisibilityScope.TEAM)));
        personRepository.saveAll(List.of(
                PersonFixtures.person(ADMIN_ID, "가동률관리자", DIVISION_ID, ADMIN_GROUP_ID),
                PersonFixtures.person(SUBJECT_ID, "가동률대상", TEAM_ID, MEMBER_GROUP_ID),
                PersonFixtures.person(CHANGING_ID, "가동률변경", OTHER_TEAM_ID, MEMBER_GROUP_ID),
                // billable=false — 집계 모집단에서만 빠진다(C1-5)
                Person.of(NON_BILLABLE_ID, "가동률비집계", TEAM_ID, 1L, MEMBER_GROUP_ID,
                        1.0, false, false, true)));

        // 같은 사람에게 두 프로젝트를 걸어 둔다 — 분자 합산이 실 질의로 성립하는지가 관건이다
        inProgressProject("가동률 프로젝트 A", SUBJECT_ID, 0.5);
        inProgressProject("가동률 프로젝트 B", SUBJECT_ID, 0.7);
        changingProjectId = inProgressProject("가동률 프로젝트 C", CHANGING_ID, 0.4);
        inProgressProject("가동률 프로젝트 D", NON_BILLABLE_ID, 1.0);
    }

    @Test
    @DisplayName("C1-1 — 두 프로젝트의 배정이 실 질의로 합산된다 (0.5+0.7 → 기본 120)")
    void sumsAssignmentsAcrossProjects() {
        // When
        UtilizationView view = only(YearMonth.of(2026, 3), SUBJECT_ID);

        // Then
        assertThat(view.assignedMm()).isEqualTo(1.2);
        assertThat(view.availableMm()).isEqualTo(1.0);
        assertThat(view.basic()).isEqualTo(120.0);
        // 보정은 계수를 곱한다 — 1.2 × 1.5 = 1.8 (2026-08-10 재정의)
        assertThat(view.adjusted()).isEqualTo(120.0 * SENIOR_COEFF);
        // C1-6 — 소속을 응답에 담아 호출자가 인원 수만큼 되묻지 않게 한다
        assertThat(view.team()).isEqualTo("가동률팀");
        assertThat(view.division()).isEqualTo("가동률사업부");
    }

    @Test
    @DisplayName("C1-4 — 배정을 바꾸면 다음 조회가 바로 새 값을 낸다 (재계산 이벤트가 없다)")
    void reflectsAssignmentChangeOnNextRead() {
        // Given: 커밋된 배정 0.4 → 기본 40
        YearMonth month = YearMonth.of(2026, 4);
        assertThat(only(month, CHANGING_ID).basic()).isEqualTo(40.0);

        // When: 배정 M/M을 바꾼다
        AssignmentView current = changingAssignment();
        assignmentService.update(ADMIN_ID, new UpdateAssignmentCommand(
                current.id(), current.startDate(), current.endDate(), 0.9, current.version()));

        // Then: 조회 시점 계산이라 재계산을 기다릴 것이 없다 — 2초가 아니라 즉시다
        assertThat(only(month, CHANGING_ID).basic()).isEqualTo(90.0);
    }

    @Test
    @DisplayName("분모 — 그 달 Capacity 행이 Person 기본값을 이긴다 (휴직·파견)")
    void monthlyCapacityOverridesDefault() {
        // Given: 2026-05만 가용 0.5로 둔다
        YearMonth month = YearMonth.of(2026, 5);
        capacityRepository.save(Capacity.of(SUBJECT_ID, month, 0.5));

        // When
        UtilizationView view = only(month, SUBJECT_ID);

        // Then: 1.2 / 0.5 = 240 (기본값 1.0을 썼다면 120이다)
        assertThat(view.availableMm()).isEqualTo(0.5);
        assertThat(view.basic()).isEqualTo(240.0);
        // 다른 달은 그대로다 — 예외는 그 달만이다
        assertThat(only(YearMonth.of(2026, 6), SUBJECT_ID).availableMm()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("C1-5 — 집계는 billable=false 인원을 모집단에서 뺀다 (개인 지정은 무관)")
    void aggregateExcludesNonBillable() {
        YearMonth month = YearMonth.of(2026, 7);

        // When: 팀 subtree 집계 — 대상 2명 중 하나가 billable=false다
        List<UtilizationView> team = utilizationQueryService.find(
                ADMIN_ID, new UtilizationQuery(month, null, TEAM_ID, false));

        // Then
        assertThat(team).extracting(UtilizationView::personId).containsExactly(SUBJECT_ID);
        // 개인 지정은 C1-5와 무관하다 — 자기 가동률은 나온다
        assertThat(only(month, NON_BILLABLE_ID).basic()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("C1-3 — overbooked 목록은 기본 가동률 100 초과인 사람만 낸다")
    void overbookedListJudgesOnBasic() {
        YearMonth month = YearMonth.of(2026, 8);

        List<UtilizationView> overbooked = utilizationQueryService.find(
                ADMIN_ID, new UtilizationQuery(month, null, TEAM_ID, true));

        // 대상 120% · 비집계 100%(경계는 초과가 아니다, 그리고 C1-5로도 빠진다)
        assertThat(overbooked).extracting(UtilizationView::personId).containsExactly(SUBJECT_ID);
    }

    @Test
    @DisplayName("가시성 밖 인원의 가동률은 404 — 팀 scope 화자에게 부문은 보이지 않는다")
    void outsideVisibilityIsNotFound() {
        // SUBJECT는 TEAM scope(가동률팀)이고 ADMIN은 그 위 부문에 있다
        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() ->
                utilizationQueryService.find(SUBJECT_ID,
                        new UtilizationQuery(YearMonth.of(2026, 9), ADMIN_ID, null, false)));
    }

    // --- 픽스처 --------------------------------------------------------------

    private UtilizationView only(YearMonth month, long personId) {
        List<UtilizationView> found = utilizationQueryService.find(
                ADMIN_ID, new UtilizationQuery(month, personId, null, false));

        assertThat(found).hasSize(1);

        return found.getFirst();
    }

    /** 계약대기 → 수주확정 → 진행중까지 밟은 프로젝트 — 분자는 진행중만 센다. */
    private long inProgressProject(String name, long personId, double monthlyMm) {
        long projectId = projectCommandService.create(ADMIN_ID, new CreateProjectCommand(
                "(주)가동률고객",
                name,
                "검색엔진",
                Engagement.REMOTE,
                2.0,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                List.of(new AssignmentSpec(personId, ProjectRole.PM, null, null, monthlyMm))))
                .id();
        advanceToInProgress(projectId);

        return projectId;
    }

    private void advanceToInProgress(long projectId) {
        long confirmed = projectCommandService
                .edit(ADMIN_ID, editTo(projectId, ProjectStatus.ORDER_CONFIRMED))
                .version();
        projectCommandService.edit(ADMIN_ID,
                editTo(projectId, ProjectStatus.IN_PROGRESS, confirmed));
    }

    private EditProjectCommand editTo(long projectId, ProjectStatus status) {
        return editTo(projectId, status, projectQueryService.getProject(ADMIN_ID, projectId).version());
    }

    private EditProjectCommand editTo(long projectId, ProjectStatus status, long version) {
        ProjectDetail current = projectQueryService.getProject(ADMIN_ID, projectId);

        return new EditProjectCommand(projectId, current.client(), current.name(),
                current.solution(), current.engagement(), current.contractMm(),
                current.startDate(), current.endDate(), status, version);
    }


    private AssignmentView changingAssignment() {
        return projectQueryService.getProject(ADMIN_ID, changingProjectId).assignments().getFirst();
    }
}
