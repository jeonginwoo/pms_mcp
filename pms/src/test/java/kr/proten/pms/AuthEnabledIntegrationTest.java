package kr.proten.pms;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 인증을 켠 상태의 관통 검증 — `pms.auth.enabled=true`.
 *
 * 평소 구성은 인증이 꺼진 상태(2026-08-21 결정: 만들어 두고 나중에 쓴다)라, 켰을 때
 * 실제로 동작하는지는 이 테스트만이 증명한다. 시드 계정으로 로그인해 실 토큰을 받고
 * 보호 자원까지 관통하며, access·refresh 교차 오용이 막히는지도 함께 본다.
 *
 * 컨테이너를 따로 띄운다: 인증 스위치와 시드가 켜진 별도 컨텍스트다.
 */
// JVM 종료 시 "Unsuccessful: drop ..." 로그가 남는다 — 컨테이너가 컨텍스트 캐시보다
// 먼저 내려가는 순서 문제로 무해(일회용 컨테이너라 drop 자체가 불요)
@SpringBootTest(properties = {
        "pms.auth.enabled=true",
        "pms.seed.path=../reference/seed"})
@AutoConfigureMockMvc
@Testcontainers
class AuthEnabledIntegrationTest {
    // 시드 계정 — 박재완(1) 관리자·전사 가시성 / 남진식(28) 팀원·소속 팀 범위
    private static final String ADMIN_EMAIL = "pro0001@proten.co.kr";
    private static final String MEMBER_EMAIL = "20230008@proten.co.kr";
    private static final String SEED_PASSWORD = "proten1!";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("로그인 — 시드 계정·초기 비밀번호로 토큰 쌍을 받는다")
    void login_withSeedAccount_issuesTokenPair() throws Exception {
        mockMvc.perform(loginRequest(ADMIN_EMAIL, SEED_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("로그인 실패 — 없는 계정과 틀린 비밀번호가 같은 401·같은 문구다")
    void login_failures_convergeToSameResponse() throws Exception {
        String unknownAccount = mockMvc.perform(loginRequest("nobody@proten.co.kr", SEED_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
                .andReturn().getResponse().getContentAsString();
        String wrongPassword = mockMvc.perform(loginRequest(ADMIN_EMAIL, "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // traceId만 다르고 code·message는 같아야 한다 — 사유가 갈라지면 계정 탐지가 된다
        Assertions.assertThat(messageOf(unknownAccount))
                .isEqualTo(messageOf(wrongPassword));
    }

    @Test
    @DisplayName("보호 자원 — 토큰 없이 호출하면 401 에러 봉투")
    void protectedResource_withoutToken_isUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/people"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("보호 자원 — access 토큰의 subject가 화자가 되어 가시성이 적용된다")
    void protectedResource_withAccessToken_appliesCallerVisibility() throws Exception {
        mockMvc.perform(get("/api/people")
                        .header("Authorization", "Bearer " + accessToken(ADMIN_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(43));

        mockMvc.perform(get("/api/people")
                        .header("Authorization", "Bearer " + accessToken(MEMBER_EMAIL)))
                .andExpect(status().isOk())
                // 팀원 scope는 TEAM이다(2026-08-22 결정) — CS사업팀 4명이 보인다
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.name == '남진식')]").exists());
    }

    @Test
    @DisplayName("인증이 켜져도 헤더로는 통과하지 못한다 — 스위치가 실제로 닫는다")
    void protectedResource_withCallerHeaderOnly_isUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/people").header("X-Caller-Person-Id", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("교차 오용 차단 — refresh 토큰으로 API를 호출할 수 없다")
    void protectedResource_withRefreshToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/people")
                        .header("Authorization", "Bearer " + refreshToken(ADMIN_EMAIL)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("갱신 — refresh 토큰으로 새 쌍을 받고, access 토큰으로는 거절된다")
    void refresh_rotatesWithRefreshTokenOnly() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("refreshToken", refreshToken(ADMIN_EMAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("refreshToken", accessToken(ADMIN_EMAIL))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWKS — 토큰 없이 열려 있고 공개키만 담긴다")
    void jwks_isPublicAndCarriesNoPrivateKey() throws Exception {
        String jwks = mockMvc.perform(get("/api/auth/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andReturn().getResponse().getContentAsString();

        // RSA 개인키 성분(d·p·q)이 새면 서명 위조가 가능하다
        Assertions.assertThat(jwks)
                .doesNotContain("\"d\"").doesNotContain("\"p\"").doesNotContain("\"q\"");
    }

    private String accessToken(String email) throws Exception {
        return tokenField(email, "accessToken");
    }

    private String refreshToken(String email) throws Exception {
        return tokenField(email, "refreshToken");
    }

    private String tokenField(String email, String field) throws Exception {
        String response = mockMvc.perform(loginRequest(email, SEED_PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return extract(response, field);
    }

    private MockHttpServletRequestBuilder loginRequest(
            String email, String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    private String body(String field, String value) {
        return "{\"%s\":\"%s\"}".formatted(field, value);
    }

    private String messageOf(String errorEnvelope) {
        return extract(errorEnvelope, "message");
    }

    /** 응답에서 문자열 필드 하나만 뽑는다 — 본문 전체 역직렬화가 필요한 검증이 아니다. */
    private String extract(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();

        return json.substring(start, json.indexOf('"', start));
    }
}
