package kr.proten.pms.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    static String searchMaintenance(String keyword) {
        return request(7, "search_maintenance", "\"keyword\":\"%s\"".formatted(keyword));
    }

    static String searchMaintenanceByStatus(String status) {
        return request(8, "search_maintenance", "\"status\":\"%s\"".formatted(status));
    }

    static String listMaintenanceLogs(int id) {
        return request(9, "list_maintenance_logs", "\"id\":%d".formatted(id));
    }

    static String listMaintenanceLogs(int id, String type) {
        return request(10, "list_maintenance_logs",
                "\"id\":%d,\"type\":\"%s\"".formatted(id, type));
    }

    /** tools/call 요청 한 줄 — 인자 JSON만 호출자가 만든다. */
    private static String request(int id, String tool, String arguments) {
        return ("{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"%s\",\"arguments\":{%s}}}")
                .formatted(id, tool, arguments);
    }

    /**
     * 도구 응답 안의 숫자 필드 — 도구가 준 id를 다음 도구에 넣는 흐름(eval C류의
     * "id 확보 경로")을 테스트에서 그대로 밟기 위한 최소 추출기다. 응답 JSON이 텍스트
     * 콘텐트 안에 이스케이프된 채 실려 오므로 파서를 세우지 않고 필드만 집어낸다.
     */
    static int intFieldOf(String body, String field) {
        Matcher matcher = Pattern.compile(field + "[^0-9-]{0,8}(-?[0-9]+)").matcher(body);

        assertThat(matcher.find()).as(field + " 필드가 응답에 없다: " + body).isTrue();

        return Integer.parseInt(matcher.group(1));
    }

    /**
     * 응답에 실린 첫 이슈의 id — 계약 → 이슈로 파고드는 흐름(eval C-04가 전제하는
     * 이슈 id 직접 제공형)을 테스트에서 밟기 위한 것. `contractId`와 헷갈리지 않게
     * `issues` 배열 안쪽만 본다.
     */
    static int firstIssueIdOf(String body) {
        int issues = body.indexOf("issues");

        assertThat(issues).as("issues 배열이 응답에 없다: " + body).isNotNegative();

        return intFieldOf(body.substring(issues), "id");
    }

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
