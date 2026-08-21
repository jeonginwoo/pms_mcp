package kr.proten.pms.identity;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import kr.proten.pms.identity.internal.application.PasswordHasher;
import kr.proten.pms.identity.internal.domain.Grade;
import kr.proten.pms.identity.internal.domain.NotifPrefs;
import kr.proten.pms.identity.internal.domain.OrgUnit;
import kr.proten.pms.identity.internal.domain.PermissionGroup;
import kr.proten.pms.identity.internal.domain.Person;
import kr.proten.pms.identity.internal.domain.User;
import kr.proten.pms.identity.internal.domain.VisibilityScope;
import kr.proten.pms.identity.internal.domain.repository.GradeRepository;
import kr.proten.pms.identity.internal.domain.repository.OrgUnitRepository;
import kr.proten.pms.identity.internal.domain.repository.PermissionGroupRepository;
import kr.proten.pms.identity.internal.domain.repository.PersonRepository;
import kr.proten.pms.identity.internal.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 로그인→토큰→보호 자원 관통 검증 (H2 웹 슬라이스 — 인증 의미론·에러 봉투 검증이
 * 목적이라 SQL 방언 무관. 방언 타는 질의가 생기는 M1b부터 Testcontainers PG 도입).
 * 시드 정합 픽스처: 전세아(합집합 키스톤 페르소나) 상정.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OrgUnitRepository orgUnitRepository;
    @Autowired
    private GradeRepository gradeRepository;
    @Autowired
    private PermissionGroupRepository permissionGroupRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordHasher passwordHasher;

    // 픽스처로 저장된 person id (토큰 sub 검증용)
    private Long personId;
    // 픽스처 계정 email — 매 테스트 고유값(컨텍스트 공유 H2에서 unique 충돌 방지)
    private String email;

    @BeforeEach
    void setUp() {
        OrgUnit company = orgUnitRepository.save(new OrgUnit(null, null, "프로텐", 0L));
        OrgUnit team = orgUnitRepository.save(new OrgUnit(null, company.id(), "AI팀", 0L));
        Grade grade = gradeRepository.save(new Grade(null, "책임", 1.3, 0L));
        PermissionGroup member = permissionGroupRepository.save(new PermissionGroup(
                null, "팀원", VisibilityScope.SELF, false, false, false, false, false, 0L));
        Person person = personRepository.save(new Person(
                null, "전세아", team.id(), grade.id(), member.id(), 1.0, true, false, true, 0L));
        personId = person.id();
        email = "jsa" + personId + "@proten.co.kr";
        userRepository.save(new User(
                null, personId, email, passwordHasher.hash("proten1!"), null,
                NotifPrefs.allOn(), 0L));
    }

    private String loginBody(String emailValue, String password) throws Exception {
        return objectMapper.writeValueAsString(
                new LoginPayload(emailValue, password));
    }

    /** 직렬화용 로그인 페이로드. */
    private record LoginPayload(String email, String password) {
    }

    /** 직렬화용 갱신 페이로드. */
    private record RefreshPayload(String refreshToken) {
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "proten1!")))
                .andExpect(status().isOk())
                .andReturn();

        return result.getResponse().getContentAsString();
    }

    private String field(String json, String name) throws Exception {
        return objectMapper.readTree(json).get(name).asString();
    }

    @Test
    @DisplayName("로그인 성공 — access·refresh 쌍 반환")
    void login_validCredentials_returnsTokenPair() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "proten1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()));
    }

    @Test
    @DisplayName("비밀번호 불일치 — 401 UNAUTHENTICATED 봉투")
    void login_wrongPassword_returns401Envelope() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.error.traceId", notNullValue()));
    }

    @Test
    @DisplayName("email 형식 오류 — 400 VALIDATION_ERROR 봉투")
    void login_malformedEmail_returns400Envelope() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("not-an-email", "proten1!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("email"));
    }

    @Test
    @DisplayName("무토큰 보호 자원 — 401 UNAUTHENTICATED 봉투 (게이트 M0 인증 케이스 예행)")
    void protectedResource_withoutToken_returns401Envelope() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("access 토큰으로 /api/me — 본인 반환")
    void me_withAccessToken_returnsSelf() throws Exception {
        String accessToken = field(login(), "accessToken");

        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personId").value(personId))
                .andExpect(jsonPath("$.name").value("전세아"))
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    @DisplayName("refresh — 새 쌍으로 회전, 새 access로 보호 자원 접근 가능")
    void refresh_validToken_rotatesAndGrantsAccess() throws Exception {
        String refreshToken = field(login(), "refreshToken");

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshPayload(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andReturn();
        String newAccess = field(refreshed.getResponse().getContentAsString(), "accessToken");

        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("refresh 자리에 access 토큰 — 401 (token_type 구분)")
    void refresh_withAccessToken_returns401() throws Exception {
        String accessToken = field(login(), "accessToken");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshPayload(accessToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("access 자리에 refresh 토큰 — 401 (리소스 서버 token_type 강제)")
    void me_withRefreshToken_returns401() throws Exception {
        String refreshToken = field(login(), "refreshToken");

        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWKS 공개 — 서명 검증용 공개키 1개 (구현_노트 §1-1 디코더 소비 지점)")
    void jwks_isPublicAndExposesSigningKey() throws Exception {
        mockMvc.perform(get("/api/auth/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys", hasSize(1)))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"));
    }
}
