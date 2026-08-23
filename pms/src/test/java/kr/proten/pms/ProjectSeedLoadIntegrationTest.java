package kr.proten.pms;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.proten.pms.person.WorkforceDirectoryService;
import kr.proten.pms.person.WorkforceProfile;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.project.AssignmentDirectoryService;
import kr.proten.pms.project.MonthlyAssignment;
import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.entity.Engagement;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAssignment;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
    private AssignmentDirectoryService assignmentDirectory;
    @Autowired
    private WorkforceDirectoryService workforceDirectory;

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
        Map<Long, WorkforceProfile> profiles = workforceDirectory.findProfiles(personIds()).stream()
                .collect(Collectors.toMap(WorkforceProfile::personId, profile -> profile));

        // 모집단 = 진행중 프로젝트의 배정만 (2026-08-10 — 전 상태를 세면 1171%로 왜곡된다)
        Map<Long, Double> assigned =
                assignmentDirectory.findInMonth(YearMonth.of(2026, 8), personIds()).stream()
                        .filter(row -> row.projectStatus() == ProjectStatus.IN_PROGRESS)
                        .collect(Collectors.groupingBy(
                                MonthlyAssignment::personId,
                                Collectors.summingDouble(MonthlyAssignment::monthlyMm)));

        assertThat(overbooked(assigned, profiles, false))
                .as("M/M 부여 규칙 자체 — billable을 거르기 전 기본 가동률 100% 초과")
                .containsExactly("이현창 191%", "윤종헌 182%", "김경민 133%");

        // C1-5: billable=false는 집계·overbooked 목록의 모집단에서 빠진다. 윤종헌(AX영업팀)이
        // 여기서 걸러져 2명이 된다 — 부록 B의 "3명"은 구 익명 명부(people.json) 기준 수치다.
        assertThat(overbooked(assigned, profiles, true))
                .containsExactly("이현창 191%", "김경민 133%");
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

    private List<String> overbooked(
            Map<Long, Double> assigned,
            Map<Long, WorkforceProfile> profiles,
            boolean billableOnly) {
        return assigned.entrySet().stream()
                .filter(entry -> !billableOnly || profiles.get(entry.getKey()).billable())
                .filter(entry -> pct(entry.getValue(), profiles.get(entry.getKey())) > 100)
                .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .map(entry -> "%s %d%%".formatted(
                        profiles.get(entry.getKey()).name(),
                        pct(entry.getValue(), profiles.get(entry.getKey()))))
                .toList();
    }

    private static int pct(double assignedMm, WorkforceProfile profile) {
        return (int) Math.round(assignedMm / profile.defaultCapacity() * 100);
    }
}
