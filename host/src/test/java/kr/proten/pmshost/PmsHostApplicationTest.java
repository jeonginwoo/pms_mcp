package kr.proten.pmshost;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.ai.anthropic.api-key=test-key")
class PmsHostApplicationTest {

    @Test
    @DisplayName("컨텍스트 기동 — Anthropic 자동 구성 + 수동 MCP 배선이 함께 선다")
    void contextLoads() {
    }

}
