package kr.proten.pmsmock.mcp;

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

import kr.proten.pmsmock.TestJwt;

/**
 * B2-2 JWT 권한 흐름 — M0 게이트 인증 3케이스의 예행연습 (구현_노트 §1-4·B2-2).
 * ① 무토큰 401 ② 타 audience 401 ③ 정상 토큰 → whoami가 그 사용자 반환.
 * 추가: 위조 서명 401 · 화자 전환(토큰별 caller 해석 — 프로퍼티 고정 폐지 실증).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpJwtAuthTest {

    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25",\
            "capabilities":{},"clientInfo":{"name":"b2-2-test","version":"0.1"}}}""";
    private static final String INITIALIZED = """
            {"jsonrpc":"2.0","method":"notifications/initialized"}""";
    private static final String WHOAMI = """
            {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"whoami","arguments":{}}}""";
    private static final String SEARCH_ALL = """
            {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"search_projects","arguments":{}}}""";
    private static final String DETAIL_361 = """
            {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"search_projects",\
            "arguments":{"projectId":361}}}""";

    @LocalServerPort
    int port;

    @Value("${mock.jwt.secret}")
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

    /** initialize → initialized 핸드셰이크 후 도구를 호출하고 응답 본문을 돌려준다 */
    private String callTool(String token, String toolCallBody) {
        HttpResponse<String> init = post(INITIALIZE, token, Map.of());
        assertThat(init.statusCode()).as("initialize").isEqualTo(200);

        Map<String, String> extra = new HashMap<>();
        extra.put("MCP-Protocol-Version", "2025-11-25");
        init.headers().firstValue("Mcp-Session-Id")
                .ifPresent(session -> extra.put("Mcp-Session-Id", session));
        post(INITIALIZED, token, extra);

        HttpResponse<String> res = post(toolCallBody, token, extra);
        assertThat(res.statusCode()).as("tools/call").isEqualTo(200);
        return res.body();
    }

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
    void 위조_서명_401() {
        String forged = TestJwt.mint("wrong-secret-wrong-secret-wrong-secret-000000", 18, "전세아", "pms");
        assertThat(post(INITIALIZE, forged, Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    void 정상_토큰_whoami가_토큰_사용자를_반환() {
        String body = callTool(TestJwt.mint(secret, 18, "전세아", "pms"), WHOAMI);
        assertThat(body).contains("전세아");
        assertThat(body).doesNotContain("isError\":true");
    }

    @Test
    void 화자_전환_토큰별로_caller가_달라진다() {
        String admin = callTool(TestJwt.mint(secret, 1, "신현랑", "pms"), WHOAMI);
        assertThat(admin).contains("신현랑").doesNotContain("전세아");
    }

    @Test
    void 가시성_팀원과_부문장_토큰의_검색_결과가_다르다() {
        // 361 한국수출입은행: team=AX솔루션사업부(부문 직속)·배정 없음 —
        // 부문장(13 정태휘)에겐 보이고 타 팀 팀원(18 전세아)에겐 안 보인다
        String member = callTool(TestJwt.mint(secret, 18, "전세아", "pms"), SEARCH_ALL);
        String head = callTool(TestJwt.mint(secret, 13, "정태휘", "pms"), SEARCH_ALL);
        assertThat(head).contains("한국수출입은행");
        assertThat(member).doesNotContain("한국수출입은행");
    }

    @Test
    void 가시성_밖_단건_지정은_404_은닉_문구() {
        String body = callTool(TestJwt.mint(secret, 18, "전세아", "pms"), DETAIL_361);
        assertThat(body).contains("조회 가능한 범위에서 해당 데이터를 찾을 수 없습니다");
    }
}
