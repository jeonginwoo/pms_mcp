package kr.proten.pms.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.proten.pms.identity.internal.domain.OrgUnit;
import kr.proten.pms.identity.internal.domain.PermissionGroup;
import kr.proten.pms.identity.internal.domain.Person;
import kr.proten.pms.identity.internal.domain.repository.GradeRepository;
import kr.proten.pms.identity.internal.domain.repository.OrgUnitRepository;
import kr.proten.pms.identity.internal.domain.repository.PermissionGroupRepository;
import kr.proten.pms.identity.internal.domain.repository.PersonRepository;
import kr.proten.pms.identity.internal.domain.repository.UserRepository;
import kr.proten.pms.identity.internal.seed.IdentitySeedLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * 시드 적재(identity분) 관통 검증 — 실 people.json 44명 + 부록 B 확정 규칙을
 * Testcontainers PostgreSQL에 적재하고 매핑·멱등성·로그인 관통을 확인한다.
 * pms.seed.path를 여기서만 켠다 — 다른 테스트는 미설정(비활성)이라 픽스처와
 * 충돌하지 않는다.
 */
// JVM 종료 시 "Unsuccessful: drop ..." 로그가 남는다 — 컨테이너가 컨텍스트 캐시보다
// 먼저 내려가는 순서 문제로 무해(테스트 판정과 무관, 일회용 컨테이너라 drop 자체가 불요)
@SpringBootTest(properties = "pms.seed.path=../reference/seed")
@AutoConfigureMockMvc
@Testcontainers
class IdentitySeedLoadIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private IdentitySeedLoader seedLoader;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GradeRepository gradeRepository;
    @Autowired
    private OrgUnitRepository orgUnitRepository;
    @Autowired
    private PermissionGroupRepository permissionGroupRepository;

    @Test
    @DisplayName("적재 규모 — 인원 44+시스템 1·직급 9·그룹 4·조직 노드 18(root+부문 6+팀 11)")
    void load_countsMatchSeedAndAppendixB() {
        assertThat(personRepository.findAll()).hasSize(45);
        assertThat(gradeRepository.findAll()).hasSize(9);
        assertThat(permissionGroupRepository.findAll()).hasSize(4);
        assertThat(orgUnitRepository.findAll()).hasSize(18);
    }

    @Test
    @DisplayName("인원 id = 시드 id 정합 — 후속 시드·eval 참조 전제 (CS사업팀 실무 3명)")
    void load_preservesSeedPersonIds() {
        assertThat(personRepository.findById(26L).orElseThrow().name()).isEqualTo("노도온");
        assertThat(personRepository.findById(27L).orElseThrow().name()).isEqualTo("한은율");
        assertThat(personRepository.findById(28L).orElseThrow().name()).isEqualTo("송수람");
    }

    @Test
    @DisplayName("조직 트리 — 대표는 root 직속, team==division 인원은 부문 노드 직속")
    void load_orgTreeAttachment() {
        Map<Long, OrgUnit> units = orgUnitRepository.findAll().stream()
                .collect(Collectors.toMap(OrgUnit::id, Function.identity()));

        // 신현랑(1, 프로텐/프로텐) → root (parentId null)
        OrgUnit ceoUnit = units.get(personRepository.findById(1L).orElseThrow().orgUnitId());
        assertThat(ceoUnit.isRoot()).isTrue();
        assertThat(ceoUnit.name()).isEqualTo("프로텐");

        // 권태휘(14, AX솔루션사업부/AX솔루션사업부) → 부문 노드 (root 바로 아래)
        OrgUnit divisionUnit = units.get(personRepository.findById(14L).orElseThrow().orgUnitId());
        assertThat(divisionUnit.name()).isEqualTo("AX솔루션사업부");
        assertThat(units.get(divisionUnit.parentId()).isRoot()).isTrue();

        // 노도온(26, CS사업팀/AX솔루션사업부) → 팀 노드 (부문 아래)
        OrgUnit teamUnit = units.get(personRepository.findById(26L).orElseThrow().orgUnitId());
        assertThat(teamUnit.name()).isEqualTo("CS사업팀");
        assertThat(units.get(teamUnit.parentId()).name()).isEqualTo("AX솔루션사업부");
    }

    @Test
    @DisplayName("권한 그룹 매핑 — 관리자 1+시스템 1·부문장 5·팀장 11·팀원 27")
    void load_groupMappingCounts() {
        Map<Long, String> groupNames = permissionGroupRepository.findAll().stream()
                .collect(Collectors.toMap(PermissionGroup::id, PermissionGroup::name));
        Map<String, Long> countsByGroup = personRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        person -> groupNames.get(person.groupId()), Collectors.counting()));

        assertThat(countsByGroup).containsEntry("관리자", 2L)
                .containsEntry("부문장", 5L)
                .containsEntry("팀장", 11L)
                .containsEntry("팀원", 27L);
    }

    @Test
    @DisplayName("billable=false — 3부문 10명 + 시스템 계정 (부록 B)")
    void load_billableFlagPopulation() {
        List<Person> people = personRepository.findAll();
        assertThat(people.stream().filter(p -> !p.system() && !p.billable()).count()).isEqualTo(10);
        assertThat(people.stream().filter(Person::system).findFirst().orElseThrow().billable()).isFalse();
    }

    @Test
    @DisplayName("시스템 관리자 계정 — admin@proten.co.kr·관리자 그룹·system=true")
    void load_systemAdminAccount() {
        Person systemAdmin = personRepository.findById(
                userRepository.findByEmail("admin@proten.co.kr").orElseThrow().personId()).orElseThrow();

        assertThat(systemAdmin.system()).isTrue();
        assertThat(permissionGroupRepository.findById(systemAdmin.groupId()).orElseThrow().name())
                .isEqualTo("관리자");
    }

    @Test
    @DisplayName("멱등성 — 재실행해도 추가 적재 없음")
    void load_secondRunIsNoop() {
        seedLoader.run(new DefaultApplicationArguments());

        assertThat(personRepository.findAll()).hasSize(45);
        assertThat(orgUnitRepository.findAll()).hasSize(18);
    }

    @Test
    @DisplayName("관통 — 시드 email·초기 비밀번호 로그인 → 관리자 가시성 인력 44명")
    void load_loginAndListPeopleEndToEnd() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mgecul13@proten.co.kr\",\"password\":\"proten1!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(body).get("accessToken").asString();

        mockMvc.perform(get("/api/people").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(44))
                .andExpect(jsonPath("$[?(@.name == '시스템 관리자')]").doesNotExist());
    }
}
