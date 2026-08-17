package kr.proten.pmshost.mcp;

import java.net.URI;
import java.net.http.HttpRequest;

import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PmsMcpConnectorTest {

    @Test
    @DisplayName("모든 /mcp HTTP 요청에 사용자 토큰이 Bearer로 실린다 — 패스스루(원칙 4)")
    void bearerCustomizerAddsAuthorizationHeader() {
        URI uri = URI.create("http://localhost:8090/mcp");
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri);

        PmsMcpConnector.bearer("test-token-123")
                .customize(builder, "POST", uri, "{}", McpTransportContext.EMPTY);

        HttpRequest request = builder.build();
        assertThat(request.headers().firstValue("Authorization"))
                .contains("Bearer test-token-123");
    }

}
