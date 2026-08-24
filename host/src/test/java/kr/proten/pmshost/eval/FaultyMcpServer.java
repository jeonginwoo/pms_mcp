package kr.proten.pmshost.eval;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * H-01 오류 주입 장치 — <b>도구 호출이 점검 응답을 돌려주는 서버</b>.
 * FR-AI-26이 요구하는 것은 "도구 호출 → 오류 수신 → 안내"이므로, 서버가 아예
 * 죽어 있으면 안 된다: 그러면 연결 단계에서 끊겨 모델이 오류를 <b>볼 기회조차</b>
 * 없고, 채점 대상(모델의 안내 문구)이 생기지 않는다.
 *
 * <p><b>왜 프록시가 아닌가.</b> 처음에는 실 서버로 통과시키고 tools/call만 가로채는
 * 프록시를 생각했지만, Streamable HTTP 응답 형식(SSE/JSON)을 중계가 보존해야 해서
 * 실패 가능성이 실 서버 쪽 사정에 묶인다. 대신 <b>카탈로그만 실 서버에서 받아 와</b>
 * (러너가 이미 갖고 있는 MCP 클라이언트로) 그대로 되돌려준다 — 도구 문구가 코드에
 * 복제되지 않으므로 카탈로그가 바뀌어도 이 장치는 따라온다.
 *
 * <p>오류 문구는 pms `ToolError`의 형식을 그대로 따른다 — `[코드] 안내문`.
 * 모델이 보는 오류의 모양이 실서버와 다르면 H-01이 채점하는 것이 실제 장애 때의
 * 행동이 아니게 된다.
 */
final class FaultyMcpServer implements AutoCloseable {

    /** FR-AI-26 표준 형식(오류 코드 + 안내문 + 재시도 여부) — 점검 안내 우선 */
    static final String MAINTENANCE_MESSAGE =
            "[503 UNAVAILABLE] 시스템 점검 중입니다. 점검이 끝난 뒤 다시 시도해 주세요.";

    private static final Pattern METHOD = Pattern.compile("\"method\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\"[^\"]*\"|\\d+)");

    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpServer server;
    private final String catalogJson;
    private final String protocolVersion;

    /**
     * @param catalog 실 서버에서 받아 둔 도구 목록 — 모델이 부를 도구가 있어야
     *                "호출 → 오류" 경로가 성립한다
     */
    FaultyMcpServer(McpSchema.ListToolsResult catalog, String protocolVersion) {
        this.protocolVersion = protocolVersion;
        try {
            this.catalogJson = json.writeValueAsString(catalog);
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (Exception e) {
            throw new IllegalStateException("오류 주입 서버를 세우지 못했다", e);
        }
        server.createContext("/mcp", this::handle);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            // GET(SSE 스트림)·DELETE(세션 종료)는 지원하지 않는다 — stateless 지향(원칙 7)
            exchange.sendResponseHeaders(405, -1);
            exchange.close();

            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String method = group(METHOD, body);
        String id = group(ID, body);

        if (id == null) {
            // 알림(notifications/*)은 응답 본문이 없다
            exchange.sendResponseHeaders(202, -1);
            exchange.close();

            return;
        }

        String payload = switch (method == null ? "" : method) {
            case "initialize" -> result(id, """
                    {"protocolVersion":"%s","capabilities":{"tools":{"listChanged":false}},\
                    "serverInfo":{"name":"pms-eval-fault","version":"0.1.0"}}"""
                    .formatted(protocolVersion));
            case "tools/list" -> result(id, catalogJson);
            case "tools/call" -> result(id, """
                    {"content":[{"type":"text","text":%s}],"isError":true}"""
                    .formatted(quote(MAINTENANCE_MESSAGE)));
            case "ping" -> result(id, "{}");
            default -> """
                    {"jsonrpc":"2.0","id":%s,"error":{"code":-32601,"message":"미지원 메서드: %s"}}"""
                    .formatted(id, method);
        };

        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String result(String id, String resultJson) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":" + resultJson + "}";
    }

    private String quote(String text) {
        try {
            return json.writeValueAsString(text);
        } catch (Exception e) {
            throw new IllegalStateException("오류 문구를 직렬화하지 못했다", e);
        }
    }

    private static String group(Pattern pattern, String body) {
        Matcher m = pattern.matcher(body);

        return m.find() ? m.group(1) : null;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
