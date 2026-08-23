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
 * MockMvc를 쓰지 않는 이유: 전송이 SSE를 함께 쓰는 함수형 엔드포인트라, 실제 포트로
 * 왕복해야 게이트가 실측한 것과 같은 경로를 지난다.
 */
final class McpHttp {

    static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25",\
            "capabilities":{},"clientInfo":{"name":"m1-promotion-test","version":"0.1"}}}""";
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

    static String findPersonByTeam(String team) {
        return """
                {"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"find_person",\
                "arguments":{"team":"%s"}}}""".formatted(team);
    }

    private final int port;
    private final HttpClient http = HttpClient.newHttpClient();

    McpHttp(int port) {
        this.port = port;
    }

    HttpResponse<String> post(String body, String token, Map<String, String> extraHeaders) {
        try {
            HttpRequest.Builder request = HttpRequest
                    .newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

            if (token != null) {
                request.header("Authorization", "Bearer " + token);
            }

            extraHeaders.forEach(request::header);

            return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("/mcp 호출 실패", e);
        }
    }

    /** /mcp 밖 경로의 상태 코드 — 체인 순서 검증용(웹은 열려 있고 /mcp만 닫힌다). */
    int statusOf(String path) {
        try {
            return http.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (Exception e) {
            throw new IllegalStateException(path + " 호출 실패", e);
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

        HttpResponse<String> response = post(body, token, extra);
        assertThat(response.statusCode()).as("request").isEqualTo(200);

        return response.body();
    }
}
