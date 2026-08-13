package kr.proten.pmshost.mcp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B2-3 관통 검증(LLM 제외 구간) — 실행 중인 목업(8090)에 실제로 붙는다.
 * 기본은 스킵: 목업을 띄운 뒤 ./gradlew test -Dpms.mcp.e2e=true 로 실행.
 * 토큰은 목업 application.yml의 HS256 시크릿 정본을 읽어 발급(중복 상수 금지).
 */
@EnabledIfSystemProperty(named = "pms.mcp.e2e", matches = "true")
class McpConnectivityIT {

    @Test
    @DisplayName("사용자 토큰으로 initialize→tools/list(8종)→whoami가 그 사용자를 반환")
    void connectsListsAndCallsWhoami() throws Exception {
        PmsMcpConnector connector = new PmsMcpConnector("http://localhost:8090");

        // sub=18 = 전세아(시드) — name 클레임은 엉뚱한 값을 넣어, 서버가 sub만
        // 신뢰하고 데이터로 화자를 해석함(B2-2 클레임 규칙)을 함께 고정한다
        try (McpSyncClient client = connector.connect(hs256Token("18", "가짜이름"))) {
            var tools = client.listTools().tools();
            assertThat(tools).hasSize(8);
            assertThat(tools).extracting(McpSchema.Tool::name).contains("whoami", "search_projects",
                    "get_utilization", "list_overbooked", "find_person", "search_maintenance",
                    "list_maintenance_logs", "update_progress");

            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("whoami", java.util.Map.of()));
            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(result.content().toString()).contains("전세아").doesNotContain("가짜이름");
        }
    }

    private static String hs256Token(String sub, String name) throws Exception {
        String yml = Files.readString(Path.of("..", "pms-mcp-mock", "src", "main", "resources", "application.yml"));
        Matcher m = Pattern.compile("secret:\\s*(\\S+)").matcher(yml);
        assertThat(m.find()).as("목업 application.yml에서 HS256 시크릿을 찾아야 한다").isTrue();
        String secret = m.group(1);

        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(("{\"sub\":\"" + sub + "\",\"name\":\"" + name
                + "\",\"aud\":\"pms\",\"channel\":\"ai-assistant\",\"exp\":"
                + Instant.now().plusSeconds(600).getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = enc.encodeToString(mac.doFinal((header + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return header + "." + payload + "." + sig;
    }

}
