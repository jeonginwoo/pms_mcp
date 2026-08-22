package kr.proten.pms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.proten.pms.auth.repository.UserRepository;
import kr.proten.pms.auth.service.AuthService;
import kr.proten.pms.person.PersonRef;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.OrgUnitService;
import kr.proten.pms.person.service.PersonService;
import kr.proten.pms.person.service.dto.CreatePersonCommand;
import kr.proten.pms.person.service.dto.OrgUnitView;
import kr.proten.pms.person.service.entity.Grade;
import kr.proten.pms.person.service.entity.OrgUnit;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.service.entity.PersonFixtures;
import kr.proten.pms.person.service.entity.VisibilityScope;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 시드 적재 관통 검증 — 실 `reference/seed/seed_org_proten.sql`을 실물 PostgreSQL에
 * 적재하고 매핑·가시성이 성립하는지 본다.
 *
 * PostgresTestBase를 쓰지 않고 컨테이너를 따로 띄운다: 이 테스트만 `pms.seed.path`를
 * 켜므로 컨텍스트가 다르고, 43명이 들어간 DB를 다른 테스트의 픽스처와 섞으면
 * "팀 범위가 정확히 N명" 같은 단정이 무너진다.
 */
// JVM 종료 시 "Unsuccessful: drop ..." 로그가 남는다 — 컨테이너가 컨텍스트 캐시보다
// 먼저 내려가는 순서 문제로 무해(일회용 컨테이너라 drop 자체가 불요)
@SpringBootTest(properties = "pms.seed.path=../reference/seed")
@Testcontainers
class PersonSeedLoadIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

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
    private OrgUnitService orgUnitService;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("적재 규모 — 조직 17 · 직급 9 · 권한 그룹 4 · 인원 44(43+시스템 계정)")
    void loads_expectedCounts() {
        assertThat(orgUnitRepository.count()).isEqualTo(17);
        assertThat(gradeRepository.count()).isEqualTo(9);
        assertThat(permissionGroupRepository.count()).isEqualTo(4);
        assertThat(personRepository.count()).isEqualTo(44);
    }

    @Test
    @DisplayName("조직 트리 — 회사 root 1개 + 부문 6 + 팀 10, 팀은 부문 아래에 있다")
    void loads_orgTreeUnderSingleRoot() {
        List<OrgUnit> units = orgUnitRepository.findAll();

        assertThat(units).filteredOn(OrgUnit::isRoot).singleElement()
                .satisfies(root -> assertThat(root.getName()).isEqualTo("(주)프로텐"));
        assertThat(units).filteredOn(unit -> Long.valueOf(1L).equals(unit.getParentId()))
                .hasSize(6);
        assertThat(units).filteredOn(unit ->
                        unit.getParentId() != null && unit.getParentId() > 1L)
                .hasSize(10);
        // MS사업부(7) 산하 3팀 — 원본 주석이 명시한 관계
        assertThat(units).filteredOn(unit -> Long.valueOf(7L).equals(unit.getParentId()))
                .map(OrgUnit::getName)
                .containsExactlyInAnyOrder("MS개발팀", "MS솔루션팀", "MOIN개발팀");
    }

    @Test
    @DisplayName("직급 계수 — 부록 B 값 그대로, 수습 없음·매니저 1.0 (보정 가동률 입력)")
    void loads_gradeCoefficients() {
        assertThat(gradeRepository.findAll())
                .extracting(Grade::getName, Grade::getCoeff)
                .containsExactlyInAnyOrder(
                        Tuple.tuple("대표이사", 2.0),
                        Tuple.tuple("부사장", 1.8),
                        Tuple.tuple("상무", 1.7),
                        Tuple.tuple("이사", 1.6),
                        Tuple.tuple("수석", 1.5),
                        Tuple.tuple("책임", 1.2),
                        Tuple.tuple("선임", 1.0),
                        Tuple.tuple("주임", 0.8),
                        Tuple.tuple("매니저", 1.0));
    }

    @Test
    @DisplayName("권한 그룹 — 기본 4종, 관리자만 systemFixed·전사 scope")
    void loads_defaultPermissionGroups() {
        assertThat(permissionGroupRepository.findAll())
                .filteredOn(group -> group.getVisibilityScope() == VisibilityScope.COMPANY)
                .singleElement()
                .satisfies(admin -> {
                    assertThat(admin.getName()).isEqualTo("관리자");
                    assertThat(admin.isSystemFixed()).isTrue();
                    assertThat(admin.isManageAllProjects()).isTrue();
                });
        assertThat(permissionGroupRepository.findAll())
                .filteredOn(group -> !group.isSystemFixed())
                .allSatisfy(group -> assertThat(group.isManageOrg()).isFalse());
    }

    @Test
    @DisplayName("그룹 배정 — 관리자 2(대표·시스템 계정) · 부문장 6 · 팀장 8 · 팀원 28")
    void loads_groupAssignmentFromOriginalRoles() {
        assertThat(countByGroup(1L)).isEqualTo(2);
        assertThat(countByGroup(2L)).isEqualTo(6);
        assertThat(countByGroup(3L)).isEqualTo(8);
        assertThat(countByGroup(4L)).isEqualTo(28);
    }

    @Test
    @DisplayName("billable — 경영관리팀·AX사업기획부 subtree 10명 제외 (부록 B 규칙)")
    void loads_billableExclusions() {
        assertThat(personRepository.findAll())
                .filteredOn(person -> !person.isBillable())
                .map(Person::getId)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 44L);
    }

    @Test
    @DisplayName("시스템 계정 — 1건이고 인력 목록에서 제외된다")
    void loads_systemAccountExcludedFromListing() {
        assertThat(personRepository.findAll()).filteredOn(Person::isSystem).singleElement()
                .satisfies(account -> assertThat(account.getName()).isEqualTo("시스템관리자"));
        // 관리자(전사 scope)로 조회해도 시스템 계정은 보이지 않는다
        assertThat(personService.listVisible(1L)).map(PersonRef::name)
                .doesNotContain("시스템관리자")
                .hasSize(43);
    }

    @Test
    @DisplayName("가시성 관통 — 팀장은 자기 팀만, 부문장은 부문 subtree, 대표는 전사")
    void visibility_worksOnRealSeed() {
        // 배성수(26) = CS사업팀 팀장 → CS사업팀 4명
        assertThat(personService.listVisible(26L)).map(PersonRef::name)
                .containsExactlyInAnyOrder("배성수", "김민환", "남진식", "이은지");
        // 김문수(16) = AX솔루션사업부 부문장 → 부문 + 산하 3팀 = 본인 1 + 4 + 4 + 4
        assertThat(personService.listVisible(16L)).hasSize(14);
        // 남진식(28) = CS사업팀 팀원 → 팀 전체 (2026-08-22 결정: 팀원 scope SELF→TEAM)
        assertThat(personService.listVisible(28L)).map(PersonRef::name)
                .containsExactlyInAnyOrder("배성수", "김민환", "남진식", "이은지");
        // 박재완(1) = 관리자(전사)
        assertThat(personService.listVisible(1L)).hasSize(43);
    }

    @Test
    @DisplayName("E3-1 — 조직 신설은 시드 id와 충돌하지 않고, 삭제한 id를 재사용하지 않는다")
    void createOrgUnit_neverReusesIds() {
        // 시드가 명시 id로 1~17을 채웠다 — 첫 신설은 그 위여야 한다
        OrgUnitView created = orgUnitService.create(1L, PersonFixtures.SI_TEAM_ID, "시퀀스확인1");
        assertThat(created.id()).isGreaterThan(17L);

        // 삭제 후 다시 만들면 같은 id가 다시 나오지 않는다 (2026-08-22 결함 회귀 방지:
        // 재사용되면 그 노드를 가리키던 비활성 인원이 새 조직 소속으로 보인다)
        orgUnitService.delete(1L, created.id());
        OrgUnitView next = orgUnitService.create(1L, PersonFixtures.SI_TEAM_ID, "시퀀스확인2");

        assertThat(next.id()).isGreaterThan(created.id());
        orgUnitService.delete(1L, next.id());
    }

    @Test
    @DisplayName("E2-1 — 등록한 인원은 시드 계정과 같은 규칙으로 로그인할 수 있다")
    void createPerson_canLogInWithInitialPassword() {
        PersonRef created = personService.create(1L, new CreatePersonCommand(
                "시드신규", PersonFixtures.SI_TEAM_ID, 1L, 4L, "seed-new@proten.co.kr"));

        assertThat(created.id()).isGreaterThan(44L);
        assertThat(authService.login("seed-new@proten.co.kr", "proten1!").accessToken())
                .isNotBlank();

        // 같은 컨텍스트를 쓰는 시드 규모 단정("인원 44")을 깨지 않도록 흔적을 지운다 —
        // 비활성으로는 행이 남으므로 이 테스트만 저장소로 직접 정리한다
        userRepository.findByEmail("seed-new@proten.co.kr")
                .ifPresent(account -> userRepository.deleteById(account.getId()));
        personRepository.deleteById(created.id());
    }

    @Test
    @DisplayName("조직명·직급명이 채워진다 — 참조가 실제로 이어졌다는 증거")
    void loads_resolvesNamesOnQuery() {
        assertThat(personService.getPerson(1L, 26L))
                .satisfies(ref -> {
                    assertThat(ref.name()).isEqualTo("배성수");
                    assertThat(ref.orgUnit()).isEqualTo("CS사업팀");
                    assertThat(ref.grade()).isEqualTo("선임");
                });
    }

    private long countByGroup(long groupId) {
        return personRepository.findAll().stream()
                .filter(person -> person.getGroupId() == groupId)
                .count();
    }
}
