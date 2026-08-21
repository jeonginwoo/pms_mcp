package kr.proten.pms.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.proten.pms.identity.internal.application.TokenProvider;
import kr.proten.pms.identity.internal.domain.Grade;
import kr.proten.pms.identity.internal.domain.OrgUnit;
import kr.proten.pms.identity.internal.domain.PermissionGroup;
import kr.proten.pms.identity.internal.domain.Person;
import kr.proten.pms.identity.internal.domain.VisibilityScope;
import kr.proten.pms.identity.internal.domain.repository.GradeRepository;
import kr.proten.pms.identity.internal.domain.repository.OrgUnitRepository;
import kr.proten.pms.identity.internal.domain.repository.PermissionGroupRepository;
import kr.proten.pms.identity.internal.domain.repository.PersonRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 가시성 필터·404 은닉 관통 검증 — Testcontainers PostgreSQL (conventions §8:
 * 방언 타는 통합 테스트는 PG 실물, H2 대체 금지 — PMS-M1b에서 도입).
 * 조직: 프로텐 → 솔루션사업부(SI팀 → SI-1파트 · CS팀) / AX사업기획부.
 * 그룹 4단(관리자·부문장·팀장·팀원) 화자별로 목록 부분집합과 단건 은닉을 검증한다.
 */
// JVM 종료 시 "Unsuccessful: drop ..." 로그가 남는다 — 컨테이너가 컨텍스트 캐시보다
// 먼저 내려가는 순서 문제로 무해(테스트 판정과 무관, 일회용 컨테이너라 drop 자체가 불요)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PeopleVisibilityIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenProvider tokenProvider;
    @Autowired
    private OrgUnitRepository orgUnitRepository;
    @Autowired
    private GradeRepository gradeRepository;
    @Autowired
    private PermissionGroupRepository permissionGroupRepository;
    @Autowired
    private PersonRepository personRepository;

    // 화자·대상 인원 (한 번만 적재 — PER_CLASS + BeforeAll)
    private Person admin;
    private Person divisionHead;
    private Person siLead;
    private Person siMember;
    private Person partMember;
    private Person csMember;
    private Person axMember;
    private Person systemAccount;
    private Person inactivePerson;

    @BeforeAll
    void seedFixture() {
        OrgUnit company = orgUnitRepository.save(new OrgUnit(null, null, "프로텐", 0L));
        OrgUnit solution = orgUnitRepository.save(new OrgUnit(null, company.id(), "솔루션사업부", 0L));
        OrgUnit siTeam = orgUnitRepository.save(new OrgUnit(null, solution.id(), "SI팀", 0L));
        OrgUnit siPart = orgUnitRepository.save(new OrgUnit(null, siTeam.id(), "SI-1파트", 0L));
        OrgUnit csTeam = orgUnitRepository.save(new OrgUnit(null, solution.id(), "CS팀", 0L));
        OrgUnit ax = orgUnitRepository.save(new OrgUnit(null, company.id(), "AX사업기획부", 0L));
        Grade grade = gradeRepository.save(new Grade(null, "책임", 1.3, 0L));

        PermissionGroup adminGroup = permissionGroupRepository.save(new PermissionGroup(
                null, "관리자", VisibilityScope.COMPANY, true, true, true, true, true, 0L));
        PermissionGroup headGroup = permissionGroupRepository.save(new PermissionGroup(
                null, "부문장", VisibilityScope.DIVISION, true, false, false, false, false, 0L));
        PermissionGroup leadGroup = permissionGroupRepository.save(new PermissionGroup(
                null, "팀장", VisibilityScope.TEAM, true, false, false, false, false, 0L));
        PermissionGroup memberGroup = permissionGroupRepository.save(new PermissionGroup(
                null, "팀원", VisibilityScope.SELF, false, false, false, false, false, 0L));

        admin = savePerson("대표", company.id(), grade.id(), adminGroup.id(), false, true);
        divisionHead = savePerson("부문장", solution.id(), grade.id(), headGroup.id(), false, true);
        siLead = savePerson("SI팀장", siTeam.id(), grade.id(), leadGroup.id(), false, true);
        siMember = savePerson("SI팀원", siTeam.id(), grade.id(), memberGroup.id(), false, true);
        partMember = savePerson("파트원", siPart.id(), grade.id(), memberGroup.id(), false, true);
        csMember = savePerson("CS팀원", csTeam.id(), grade.id(), memberGroup.id(), false, true);
        axMember = savePerson("기획팀원", ax.id(), grade.id(), memberGroup.id(), false, true);
        systemAccount = savePerson("시스템관리자", company.id(), grade.id(), adminGroup.id(), true, true);
        inactivePerson = savePerson("퇴사자", siTeam.id(), grade.id(), memberGroup.id(), false, false);
    }

    private Person savePerson(
            String name,
            Long orgUnitId,
            Long gradeId,
            Long groupId,
            boolean system,
            boolean active) {
        return personRepository.save(new Person(
                null, name, orgUnitId, gradeId, groupId, 1.0, true, system, active, 0L));
    }

    private String bearerOf(Person person) {
        return "Bearer " + tokenProvider.issue(person.id()).accessToken();
    }

    @Test
    @DisplayName("관리자(COMPANY) 목록 — 시스템 계정·비활성 제외 전원")
    void list_companyScope_returnsAllActiveNonSystem() throws Exception {
        mockMvc.perform(get("/api/people").header("Authorization", bearerOf(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[?(@.name == '시스템관리자')]").doesNotExist())
                .andExpect(jsonPath("$[?(@.name == '퇴사자')]").doesNotExist());
    }

    @Test
    @DisplayName("부문장(DIVISION) 목록 — 자기 부문 subtree만, 타부문·대표 제외")
    void list_divisionScope_returnsOwnDivisionSubtree() throws Exception {
        mockMvc.perform(get("/api/people").header("Authorization", bearerOf(divisionHead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.name == '기획팀원')]").doesNotExist())
                .andExpect(jsonPath("$[?(@.name == '대표')]").doesNotExist());
    }

    @Test
    @DisplayName("팀장(TEAM) 목록 — 하위 조직(subtree) 인원 포함 (E3-4)")
    void list_teamScope_includesSubtree() throws Exception {
        mockMvc.perform(get("/api/people").header("Authorization", bearerOf(siLead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.name == '파트원')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'CS팀원')]").doesNotExist());
    }

    @Test
    @DisplayName("팀원(SELF) 목록 — 본인만")
    void list_selfScope_returnsOnlySelf() throws Exception {
        mockMvc.perform(get("/api/people").header("Authorization", bearerOf(csMember)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("CS팀원"))
                .andExpect(jsonPath("$[0].orgUnit").value("CS팀"))
                .andExpect(jsonPath("$[0].grade").value("책임"));
    }

    @Test
    @DisplayName("단건 — 팀장은 하위 파트 인원까지 조회 (E3-4)")
    void getOne_teamScope_subtreePersonVisible() throws Exception {
        mockMvc.perform(get("/api/people/" + partMember.id())
                        .header("Authorization", bearerOf(siLead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("파트원"))
                .andExpect(jsonPath("$.orgUnit").value("SI-1파트"));
    }

    @Test
    @DisplayName("단건 — 가시성 밖·부재·시스템 계정·비활성 전부 같은 404 봉투 (은닉 동형)")
    void getOne_concealedCases_returnIdenticalNotFoundEnvelope() throws Exception {
        // 가시성 밖 (SELF 화자 → 같은 팀 동료)
        assertNotFoundEnvelope(siMember, csMember.id());
        // 부재
        assertNotFoundEnvelope(admin, 999_999L);
        // 시스템 계정 (④ 목록·조회 제외)
        assertNotFoundEnvelope(admin, systemAccount.id());
        // 비활성 (E2-3)
        assertNotFoundEnvelope(admin, inactivePerson.id());
    }

    private void assertNotFoundEnvelope(Person caller, Long targetId) throws Exception {
        mockMvc.perform(get("/api/people/" + targetId)
                        .header("Authorization", bearerOf(caller)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("해당 데이터 없음"));
    }

    @Test
    @DisplayName("무토큰 — 401 (보호 자원)")
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/people"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }
}
