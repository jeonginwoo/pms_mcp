package kr.proten.pms.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 게이트 M0 인증 3케이스 (구현_노트 §1-4) — pms-mcp-mock B2-2 테스트의 승격.
 * ① 무토큰 401 ② 타 audience 401 ③ 정상 토큰 → whoami가 그 사용자 반환(시드 실데이터).
 * 추가: 위조 서명 401 · 화자 전환 · 카탈로그 8종 노출 · 인력 가시성 ·
 * 미구현 포트의 FR-AI-26 표준 오류. 가시성 E2E 전체(프로젝트·404 은닉)는
 * 각 모듈 서비스가 port를 구현할 때 mock 테스트에서 마저 승격한다(PMS-M1).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpJwtAuthTest {

    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25",\
            "capabilities":{},"clientInfo":{"name":"m0-gate-test","version":"0.1"}}}""";
    private static final String INITIALIZED = """
            {"jsonrpc":"2.0","method":"notifications/initialized"}""";
    private static final String TOOLS_LIST = """
            {"jsonrpc":"2.0","id":2,"method":"tools/list"}""";
    private static final String WHOAMI = """
            {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"whoami","arguments":{}}}""";
    private static final String FIND_ALL_PEOPLE = """
            {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"find_person","arguments":{}}}""";
    private static final String SEARCH_PROJECTS = """
            {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"search_projects","arguments":{}}}""";

    @LocalServerPort
    int port;

    @Value("${pms.auth.hs256-secret}")
    String secret;

    final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> post(String body, String token, Map<String, String> extraHeaders) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (token != null) {
                req.header("Authorization", "Bearer " + token);
            }
            extraHeaders.forEach(req::header);
            return http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("/mcp 호출 실패", e);
        }
    }

    /** initialize → initialized 핸드셰이크 후 요청을 보내고 응답 본문을 돌려준다 */
    private String call(String token, String body) {
        HttpResponse<String> init = post(INITIALIZE, token, Map.of());
        assertThat(init.statusCode()).as("initialize").isEqualTo(200);

        Map<String, String> extra = new HashMap<>();
        extra.put("MCP-Protocol-Version", "2025-11-25");
        init.headers().firstValue("Mcp-Session-Id")
                .ifPresent(session -> extra.put("Mcp-Session-Id", session));
        post(INITIALIZED, token, extra);

        HttpResponse<String> res = post(body, token, extra);
        assertThat(res.statusCode()).as("request").isEqualTo(200);
        return res.body();
    }

    // --- 게이트 M0 인증 3케이스 ---------------------------------------------

    @Test
    void 무토큰_401() {
        assertThat(post(INITIALIZE, null, Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    void 타_audience_401() {
        String wrongAud = TestJwt.mint(secret, 18, "전세아", "other-service");
        assertThat(post(INITIALIZE, wrongAud, Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    void 정상_토큰_whoami가_토큰_사용자를_반환() {
        String body = call(TestJwt.mint(secret, 18, "전세아", "pms"), WHOAMI);
        assertThat(body).contains("전세아").contains("팀원");
        assertThat(body).doesNotContain("isError\":true");
    }

    // --- 추가 방어선 (B2-2 승격) --------------------------------------------

    @Test
    void 위조_서명_401() {
        String forged = TestJwt.mint("wrong-secret-wrong-secret-wrong-secret-000000", 18, "전세아", "pms");
        assertThat(post(INITIALIZE, forged, Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    void 화자_전환_토큰별로_caller가_달라진다() {
        String admin = call(TestJwt.mint(secret, 1, "신현랑", "pms"), WHOAMI);
        assertThat(admin).contains("신현랑").doesNotContain("전세아");
    }

    @Test
    void 카탈로그_8종이_그대로_노출된다() {
        String body = call(TestJwt.mint(secret, 18, "전세아", "pms"), TOOLS_LIST);
        assertThat(body).contains("whoami", "find_person", "search_projects", "get_utilization",
                "list_overbooked", "search_maintenance", "list_maintenance_logs", "update_progress");
    }

    @Test
    void 인력_가시성_팀원은_본인만_관리자는_전사() {
        // 18 전세아(팀원 그룹=본인 가시성) vs 1 신현랑(관리자 그룹=전사)
        String member = call(TestJwt.mint(secret, 18, "전세아", "pms"), FIND_ALL_PEOPLE);
        assertThat(member).contains("전세아").doesNotContain("신현랑");
        String admin = call(TestJwt.mint(secret, 1, "신현랑", "pms"), FIND_ALL_PEOPLE);
        assertThat(admin).contains("신현랑").contains("전세아");
    }

    @Test
    void 미구현_포트는_준비_중_표준_오류를_반환한다() {
        // FR-AI-26 — 도구는 노출하되 실패 사실·재시도 가능 여부를 표준 형식으로
        String body = call(TestJwt.mint(secret, 18, "전세아", "pms"), SEARCH_PROJECTS);
        assertThat(body).contains("[503 UNAVAILABLE]").contains("준비 중");
    }
}
