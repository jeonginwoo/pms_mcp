package kr.proten.pms;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.OrgUnitService;
import kr.proten.pms.person.service.dto.OrgUnitView;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.ProjectQueryService;
import kr.proten.pms.project.service.dto.ProjectSummary;
import kr.proten.pms.project.service.entity.Engagement;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAssignment;
import kr.proten.pms.project.service.entity.ProjectPhase;
import kr.proten.pms.project.service.entity.ProjectRole;
import kr.proten.pms.resource.service.UtilizationQueryService;
import kr.proten.pms.resource.service.dto.UtilizationQuery;
import kr.proten.pms.resource.service.dto.UtilizationView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 프로젝트 시드 적재 관통 검증 — 실 `reference/seed/projects.json` 382건을 실물
 * PostgreSQL에 적재하고 부록 B의 기대값이 그대로 나오는지 본다.
 *
 * <p>이 테스트가 값을 하는 지점은 <b>가동률 오버부킹</b>이다. M/M 부여 규칙(부록 B,
 * 2026-08-10)이 시드에서 어떤 수치를 만드는지가 게이트 M-1 핵심 시나리오의 전제이므로,
 * 여기서 어긋나면 시연이 그 자리에서 깨진다.
 *
 * <p>인원 정본은 `seed_org_proten.sql`(실제 명부)이다 — `people.json`은 구 익명 명부라
 * 쓰지 않는다(2026-08-23 확인). 부록 B가 적어 둔 인물 이름은 그 익명 명부 기준이므로
 * 여기서는 실제 명부의 이름을 단정한다.
 *
 * <p>인원 시드 테스트와 같은 이유로 컨테이너를 따로 띄운다 — 이 컨텍스트만
 * `pms.seed.path`를 켠다.
 */
@SpringBootTest(properties = "pms.seed.path=../reference/seed")
@Testcontainers
class ProjectSeedLoadIntegrationTest {
    private static final YearMonth MONTH = YearMonth.of(2026, 8);
    /** 박재완 — 시드에서 유일한 관리자(COMPANY scope) 실인원이다. */
    private static final long COMPANY_SCOPE_CALLER_ID = 1L;
    /** 윤종헌 — AX사업기획부라 billable=false다(시드 주석). */
    private static final long NON_BILLABLE_PERSON_ID = 7L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectAssignmentRepository assignmentRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private UtilizationQueryService utilizationQueryService;
    @Autowired
    private ProjectQueryService projectQueryService;
    @Autowired
    private OrgUnitService orgUnitService;

    @Test
    @DisplayName("부록 B — 프로젝트 382건이 상태 분포 그대로 적재된다")
    void loadsAllProjectsWithExpectedStatusMix() {
        assertThat(projectRepository.count()).isEqualTo(382);

        Map<ProjectStatus, Long> byStatus = projectRepository.findAll().stream()
                .collect(Collectors.groupingBy(Project::getStatus, Collectors.counting()));

        assertThat(byStatus).containsExactlyInAnyOrderEntriesOf(Map.of(
                ProjectStatus.COMPLETED, 319L,
                ProjectStatus.IN_PROGRESS, 34L,
                ProjectStatus.ORDER_CONFIRMED, 19L,
                ProjectStatus.CONTRACT_PENDING, 10L));
    }

    @Test
    @DisplayName("§7 ?phase= — 실 시드 382건이 영업 29 · 솔루션 353으로 갈린다")
    void phaseFilterSplitsTheRealSeed() {
        // 위 상태 분포(계약대기 10 · 수주확정 19 · 진행중 34 · 완료 319)의 파생값이다 —
        // 여기서 어긋나면 탭이 상태 분포와 다른 표를 보고 있다는 뜻이다
        assertThat(phaseCount(ProjectPhase.SALES)).isEqualTo(29);
        assertThat(phaseCount(ProjectPhase.SOLUTION)).isEqualTo(353);
        // 시드에 유지보수중이 0건이라 둘의 합이 전량이다(이관만이 그 상태를 만든다)
        assertThat(phaseCount(ProjectPhase.SALES) + phaseCount(ProjectPhase.SOLUTION))
                .isEqualTo(phaseCount(null))
                .isEqualTo(382);
    }

    @Test
    @DisplayName("§5 — 목록이 실어 주는 phase는 상태 파생값과 어긋나지 않는다")
    void listedPhaseAgreesWithStatus() {
        List<ProjectSummary> page = projectQueryService
                .listVisible(COMPANY_SCOPE_CALLER_ID, null, PageRequest.of(0, 400))
                .getContent();

        assertThat(page).hasSize(382).allSatisfy(summary ->
                assertThat(summary.phase()).isEqualTo(ProjectPhase.of(summary.status())));
    }

    private long phaseCount(ProjectPhase phase) {
        return projectQueryService
                .listVisible(COMPANY_SCOPE_CALLER_ID, phase, PageRequest.of(0, 1))
                .getTotalElements();
    }

    @Test
    @DisplayName("OFFSITE 폐지 — 시드의 32건이 REMOTE로 흡수된다")
    void convertsOffsiteToRemote() {
        Set<Engagement> used = projectRepository.findAll().stream()
                .map(Project::getEngagement)
                .collect(Collectors.toUnmodifiableSet());

        // OFFSITE는 enum에 없으므로 남아 있으면 적재 자체가 실패한다 — 여기서는
        // 32건이 사라지지 않고 REMOTE로 흡수됐는지(원격 총량)를 본다
        assertThat(used).containsExactlyInAnyOrder(
                Engagement.REMOTE, Engagement.ONSITE, Engagement.PARTIAL_ONSITE);
        assertThat(countBy(Engagement.REMOTE)).isEqualTo(256 + 32);
        assertThat(countBy(Engagement.ONSITE)).isEqualTo(18);
        assertThat(countBy(Engagement.PARTIAL_ONSITE)).isEqualTo(76);
    }

    @Test
    @DisplayName("A7-2 — 완료 프로젝트의 진척률은 예외 없이 100이다 (시드 13건 보정)")
    void completedProjectsAreAllAtHundred() {
        List<Project> completed = projectRepository.findAll().stream()
                .filter(project -> project.getStatus() == ProjectStatus.COMPLETED)
                .toList();

        assertThat(completed).hasSize(319);
        assertThat(completed).allSatisfy(project ->
                assertThat(project.getProgress()).isEqualTo(100));
    }

    @Test
    @DisplayName("A6-5 불변식 — 프로젝트마다 role=PM 정확히 1행이고 managerId와 일치한다")
    void everyProjectHasExactlyOneManagerAssignment() {
        Map<Long, Long> managerIdByProject = projectRepository.findAll().stream()
                .collect(Collectors.toMap(Project::getId, Project::getManagerId));
        Map<Long, List<ProjectAssignment>> pmRows = assignmentRepository.findAll().stream()
                .filter(assignment -> assignment.getRole() == ProjectRole.PM)
                .collect(Collectors.groupingBy(ProjectAssignment::getProjectId));

        assertThat(pmRows).hasSize(382);
        assertThat(pmRows).allSatisfy((projectId, rows) -> {
            assertThat(rows).hasSize(1);
            assertThat(rows.getFirst().getPersonId()).isEqualTo(managerIdByProject.get(projectId));
        });
    }

    @Test
    @DisplayName("배정은 실재 인원만 가리킨다 — 유령 배정 0건")
    void assignmentsReferenceRealPeople() {
        Set<Long> knownPeople = personIds();

        assertThat(assignmentRepository.findAll())
                .allSatisfy(assignment ->
                        assertThat(knownPeople).contains(assignment.getPersonId()));
    }

    @Test
    @DisplayName("2026-08 오버부킹 — 이현창·김경민 (C1-5로 윤종헌 제외)")
    void reproducesOverbookingFromSeed() {
        // 산식을 여기서 다시 쓰지 않는다(2026-08-23): EPIC C가 들어오기 전에는 이 테스트가
        // 배정 합산·분모·>100 판정을 자체 구현하고 있었고, 그러면 정본이 두 벌이 된다.
        List<UtilizationView> overbooked = utilizationQueryService.find(
                COMPANY_SCOPE_CALLER_ID, new UtilizationQuery(MONTH, null, null, true));

        // C1-5: billable=false는 집계·overbooked 목록의 모집단에서 빠진다. 윤종헌(AX영업팀)이
        // 여기서 걸러져 2명이 된다 — 부록 B의 "3명"은 구 익명 명부(people.json) 기준 수치다.
        assertThat(overbooked)
                .extracting(view -> "%s %d%%".formatted(view.name(), Math.round(view.basic())))
                .containsExactlyInAnyOrder("이현창 191%", "김경민 133%");
    }

    @Test
    @DisplayName("C1-5 — 윤종헌은 실제로 182%지만 billable=false라 집계에서만 빠진다")
    void nonBillablePersonStillHasOwnUtilization() {
        // M/M 부여 규칙 자체가 만드는 수치는 개인 지정 조회로 확인한다 — 그쪽은 C1-5와
        // 무관하다. 집계에서 사라지는 것과 "가동률이 없는 것"은 다른 말이다
        List<UtilizationView> own = utilizationQueryService.find(
                COMPANY_SCOPE_CALLER_ID, new UtilizationQuery(MONTH, NON_BILLABLE_PERSON_ID, null, false));

        assertThat(own).singleElement().satisfies(view -> {
            assertThat(view.name()).isEqualTo("윤종헌");
            assertThat(Math.round(view.basic())).isEqualTo(182L);
        });
    }

    @Test
    @DisplayName("이름이 같은 서로 다른 프로젝트 2건이 하나로 접히지 않는다")
    void keepsBothSameNamedProjects() {
        List<Project> tck = projectRepository.findAll().stream()
                .filter(project -> project.getName().equals("TCK 검색엔진 추가"))
                .toList();

        // 멱등을 이름 키로 판정하면 여기가 1건이 된다 — 실제로는 기간·PM이 다른 별건이다
        assertThat(tck).hasSize(2);
        assertThat(tck).extracting(Project::getManagerId).containsExactlyInAnyOrder(13L, 30L);
    }

    @Test
    @DisplayName("부록 A 조직 트리 — 노드별 프로젝트 수는 PM 소속으로 접힌 382건의 분할이다")
    void orgTreeProjectCountsPartitionTheSeed() {
        Map<String, Long> byNode = orgUnitService.list(COMPANY_SCOPE_CALLER_ID).stream()
                .filter(unit -> unit.projectCount() > 0)
                .collect(Collectors.toMap(OrgUnitView::name, OrgUnitView::projectCount));

        // 합이 정확히 382 — 한 프로젝트는 대표 PM 한 명을 통해 한 노드에만 걸린다.
        // 배정으로 셌다면 여기가 382를 넘는다(한 프로젝트가 여러 노드에 동시에 선다)
        assertThat(byNode.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(382);

        // **이 수는 `projects.json`의 `team` 필드가 아니다** (2026-08-26 실측):
        // 그 필드는 구 익명 명부(`people.json`) 기준이고 `managerId`는 id로만 연결돼
        // 실제 명부의 다른 사람을 가리킨다(`reference/seed/README.md`). 그래서 시드의
        // `team` 카운트(AX솔루션사업부 128 …)와 실제 분포는 300건이 어긋난다 —
        // PRD-pms §12가 근거로 든 "프로토타입 실측 11개 노드 일치"는 프로토타입의
        // 목업 데이터에서 성립한 값이고 실 앱에서는 성립하지 않는다
        assertThat(byNode).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("AI팀", 128L),
                Map.entry("AX솔루션사업부", 55L),
                Map.entry("AX솔루션개발1팀", 52L),
                Map.entry("AX솔루션개발2팀", 48L),
                Map.entry("CS사업팀", 40L),
                Map.entry("AX기획마케팅팀", 20L),
                Map.entry("MS개발팀", 14L),
                Map.entry("관리•마케팅부", 9L),
                Map.entry("AX영업팀", 8L),
                Map.entry("경영관리팀", 4L),
                Map.entry("AI기술연구소", 2L),
                Map.entry("AX개발팀", 1L),
                Map.entry("MS사업부", 1L)));
    }

    @Test
    @DisplayName("AC E3-3 — 프로젝트가 걸린 노드는 실 시드에서 하나도 삭제 가능이 아니다")
    void nodesCarryingProjectsAreNotDeletable() {
        // 화면이 보여 주는 수와 삭제 판정이 **같은 수**를 읽는지가 이 단정의 전부다
        // (2026-08-26 사용자 결정). 갈라 두면 "프로젝트 128"이라고 적힌 노드에
        // 삭제 버튼이 켜진다
        assertThat(orgUnitService.list(COMPANY_SCOPE_CALLER_ID))
                .filteredOn(unit -> unit.projectCount() > 0)
                .hasSize(13)
                .allSatisfy(unit -> assertThat(unit.deletable()).isFalse());
    }

    private Set<Long> personIds() {
        return personRepository.findAll().stream()
                .map(Person::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private long countBy(Engagement engagement) {
        return projectRepository.findAll().stream()
                .filter(project -> project.getEngagement() == engagement)
                .count();
    }
}
