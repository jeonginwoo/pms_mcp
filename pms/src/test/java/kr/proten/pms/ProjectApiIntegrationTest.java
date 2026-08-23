package kr.proten.pms;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.entity.Grade;
import kr.proten.pms.person.service.entity.PersonFixtures;
import kr.proten.pms.person.service.entity.VisibilityScope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP 관통 검증 — 실물 PostgreSQL 위에서 컨트롤러부터 DB까지 한 줄기로 확인한다.
 *
 * 웹 슬라이스 테스트(ProjectControllerTest)가 서비스를 대역으로 두고 경계만 보는
 * 반면, 여기서는 실제 서비스·리포지토리·Flyway 스키마가 붙은 상태에서 생성 →
 * 조회 → 2단계 진척률 갱신이 이어지는지를 본다.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectApiIntegrationTest extends PostgresTestBase {
    private static final String CALLER_HEADER = "X-Caller-Person-Id";
    private static final long TEAM_LEAD_ID = 202L;
    private static final long PM_ID = 203L;
    private static final long OUTSIDER_ID = 206L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrgUnitRepository orgUnitRepository;
    @Autowired
    private GradeRepository gradeRepository;
    @Autowired
    private PermissionGroupRepository permissionGroupRepository;
    @Autowired
    private PersonRepository personRepository;

    /**
     * DB가 필요한 테스트들은 컨테이너와 스프링 컨텍스트를 공유하므로 데이터도 공유한다 —
     * 다른 클래스의 "팀 범위가 정확히 N명" 같은 단정이 깨지지 않도록 이 클래스의
     * 인원은 CS팀에, 식별자는 200번대에 둔다.
     */
    @BeforeAll
    void seedFixture() {
        orgUnitRepository.saveAll(PersonFixtures.orgUnits());
        gradeRepository.save(Grade.of(1L, "수석", 1.5));
        permissionGroupRepository.saveAll(List.of(
                PersonFixtures.group(13L, "팀장", VisibilityScope.TEAM,
                        OrgPermission.CREATE_PROJECT),
                PersonFixtures.group(14L, "팀원", VisibilityScope.SELF)));
        personRepository.saveAll(List.of(
                PersonFixtures.person(TEAM_LEAD_ID, "API팀장", PersonFixtures.CS_TEAM_ID, 13L),
                PersonFixtures.person(PM_ID, "API피엠", PersonFixtures.CS_TEAM_ID, 14L),
                PersonFixtures.person(
                        OUTSIDER_ID, "API타부문", PersonFixtures.OTHER_DIVISION_ID, 14L)));
    }

    @Test
    @DisplayName("생성 → 조회 → 2단계 진척률 갱신이 HTTP로 이어진다")
    void createThenQueryThenUpdateProgress() throws Exception {
        // 생성 — 201, 계약대기, phase 파생
        String created = mockMvc.perform(post("/api/projects")
                        .header(CALLER_HEADER, TEAM_LEAD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "client": "(주)관통고객",
                                  "name": "관통 과제",
                                  "solution": "검색엔진",
                                  "engagement": "REMOTE",
                                  "contractMm": 2.0,
                                  "startDate": "2026-08-01",
                                  "endDate": "2026-12-31",
                                  "assignments": [{"personId": %d, "role": "PM", "monthlyMm": 0.5}]
                                }
                                """.formatted(PM_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONTRACT_PENDING"))
                .andExpect(jsonPath("$.data.phase").value("SALES"))
                .andExpect(jsonPath("$.data.assignments[0].personName").value("API피엠"))
                .andReturn().getResponse().getContentAsString();
        long projectId = projectIdOf(created);

        // 목록 — page 봉투로 나가고 팀장 가시성에 든다
        mockMvc.perform(get("/api/projects").header(CALLER_HEADER, TEAM_LEAD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == %d)].managerName".formatted(projectId))
                        .value("API피엠"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalElements").exists());

        // 계약대기 상태의 진척률 수정은 거절된다 (2026-08-22 결정 — 진행중에서만)
        mockMvc.perform(put("/api/projects/" + projectId + "/progress")
                        .header(CALLER_HEADER, PM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"progress\": 40, \"version\": 0, \"confirmed\": true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NOT_IN_PROGRESS"));

        // 순방향 두 칸 — 계약대기 → 수주확정 → 진행중 (version 0 → 2)
        advanceStatus(projectId, "ORDER_CONFIRMED", 0);
        advanceStatus(projectId, "IN_PROGRESS", 1);

        // 확인 전 — 요약만, DB 미변경
        mockMvc.perform(put("/api/projects/" + projectId + "/progress")
                        .header(CALLER_HEADER, PM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"progress\": 40, \"version\": 2, \"confirmed\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.committed").value(false));
        mockMvc.perform(get("/api/projects/" + projectId).header(CALLER_HEADER, PM_ID))
                .andExpect(jsonPath("$.data.progress").value(0));

        // 확인 후 — 커밋 + version 증가
        mockMvc.perform(put("/api/projects/" + projectId + "/progress")
                        .header(CALLER_HEADER, PM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"progress\": 40, \"version\": 2, \"confirmed\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.committed").value(true))
                .andExpect(jsonPath("$.data.version").value(3));

        // 지나간 version — 409
        mockMvc.perform(put("/api/projects/" + projectId + "/progress")
                        .header(CALLER_HEADER, PM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"progress\": 60, \"version\": 2, \"confirmed\": true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STALE_VERSION"));

        // 가시성 밖 화자 — 404 은닉
        mockMvc.perform(get("/api/projects/" + projectId).header(CALLER_HEADER, OUTSIDER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("생성 권한 없는 그룹은 403 — 실제 권한 그룹 판정을 거친다")
    void create_byMemberGroup_isForbidden() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .header(CALLER_HEADER, PM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "client": "(주)권한없음",
                                  "name": "거절 과제",
                                  "engagement": "REMOTE",
                                  "contractMm": 1.0,
                                  "assignments": [{"personId": %d, "role": "PM"}]
                                }
                                """.formatted(PM_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("인력 목록 — 팀장 가시성 범위가 HTTP로 그대로 나온다")
    void listPeople_appliesVisibility() throws Exception {
        mockMvc.perform(get("/api/people").header(CALLER_HEADER, TEAM_LEAD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == 'API팀장')]").exists())
                .andExpect(jsonPath("$.data[?(@.name == 'API타부문')]").doesNotExist());
    }

    @Test
    @DisplayName("없는 경로는 404 봉투 — 500이 아니다")
    void unknownRoute_isNotFoundEnvelope() throws Exception {
        mockMvc.perform(get("/api/nope").header(CALLER_HEADER, TEAM_LEAD_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("호출자 헤더 없이 호출하면 401")
    void request_withoutCallerHeader_isUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    /** 정보 수정 경로로 상태를 한 칸 올린다 (A5-1) — 진척률 경로의 전제를 만든다.
     *  화자는 PM이다: 정보 수정은 PM·PL만 가능하고(A5-3) 생성자인 팀장은 미배정이다. */
    private void advanceStatus(long projectId, String status, long version) throws Exception {
        mockMvc.perform(put("/api/projects/" + projectId)
                        .header(CALLER_HEADER, PM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "client": "(주)관통고객",
                                  "name": "관통 과제",
                                  "solution": "검색엔진",
                                  "engagement": "REMOTE",
                                  "contractMm": 2.0,
                                  "startDate": "2026-08-01",
                                  "endDate": "2026-12-31",
                                  "status": "%s",
                                  "version": %d
                                }
                                """.formatted(status, version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(status));
    }

    /** 생성 응답에서 id만 뽑는다 — 본문 전체 역직렬화가 필요한 검증이 아니다. */
    private long projectIdOf(String createdBody) {
        String marker = "\"id\":";
        int start = createdBody.indexOf(marker) + marker.length();
        int end = createdBody.indexOf(',', start);

        return Long.parseLong(createdBody.substring(start, end).trim());
    }
}
