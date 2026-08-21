package kr.proten.pms.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * /mcp Streamable HTTP 테스트 클라이언트 — initialize 핸드셰이크 공통분.
 * McpJwtAuthTest(HS256 모드)·McpJwksAuthTest(JWKS 모드)가 공유한다.
 */
final class McpHttp {

    static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25",\
            "capabilities":{},"clientInfo":{"name":"m0-gate-test","version":"0.1"}}}""";
    static final String INITIALIZED = """
            {"jsonrpc":"2.0","method":"notifications/initialized"}""";
    static final String TOOLS_LIST = """
            {"jsonrpc":"2.0","id":2,"method":"tools/list"}""";
    static final String WHOAMI = """
            {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"whoami","arguments":{}}}""";
    static final String FIND_ALL_PEOPLE = """
            {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"find_person","arguments":{}}}""";
    static final String SEARCH_PROJECTS = """
            {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"search_projects","arguments":{}}}""";

    private final int port;
    private final HttpClient http = HttpClient.newHttpClient();

    McpHttp(int port) {
        this.port = port;
    }

    HttpResponse<String> post(String body, String token, Map<String, String> extraHeaders) {
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
    String call(String token, String body) {
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
}
