package kr.proten.pmshost.mcp;

import java.time.Duration;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 사용자 토큰을 실은 PMS MCP 연결 생성 (구현_노트 §3-2).
 * 요청/대화 단위로 생성하고 사용 후 close — 앱 전역 고정 연결·전능한 서비스
 * 계정은 두지 않는다(원칙 4). 1차 규모(44명)에서는 요청마다 생성으로 충분.
 */
@Component
public class PmsMcpConnector {

    private final String pmsBaseUrl;

    public PmsMcpConnector(@Value("${pms.mcp.base-url}") String pmsBaseUrl) {
        this.pmsBaseUrl = pmsBaseUrl;
    }

    /** 이 사용자의 토큰으로 초기화까지 마친 MCP 클라이언트. 호출측이 close 책임. */
    public McpSyncClient connect(String userToken) {
        var transport = HttpClientStreamableHttpTransport.builder(pmsBaseUrl)
                .endpoint("/mcp")
                .httpRequestCustomizer(bearer(userToken))
                .build();
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .build();
        client.initialize();

        return client;
    }

    /** 모든 /mcp HTTP 요청에 사용자 토큰을 그대로 싣는다 — 패스스루(원칙 4) */
    static McpSyncHttpClientRequestCustomizer bearer(String userToken) {
        return (builder, method, endpoint, body, context) ->
                builder.header("Authorization", "Bearer " + userToken);
    }

}
