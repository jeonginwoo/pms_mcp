package kr.proten.pmsmock;

import static org.assertj.core.api.Assertions.assertThat;

import kr.proten.pmsmock.mcp.IdentityMcpTools;
import kr.proten.pmsmock.mcp.MaintenanceMcpTools;
import kr.proten.pmsmock.mcp.ProjectMcpTools;
import kr.proten.pmsmock.mcp.UtilizationMcpTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * STREAMABLE 설정으로 컨텍스트가 뜨고 MCP 도구 빈 4종이 등록되는지 확인하는 슬라이스 테스트.
 * 프로토콜 레벨(tools/list) 검증은 bootRun + curl 스모크와 Inspector(별도 ROADMAP 항목)가 맡는다.
 */
@SpringBootTest
class PmsMcpMockApplicationTests {
    // 빈 존재 확인용 컨텍스트 — 생성자 주입(필드 @Autowired 금지 규칙)
    private final ApplicationContext context;

    PmsMcpMockApplicationTests(@Autowired ApplicationContext context) {
        this.context = context;
    }

    @Test
    @DisplayName("STREAMABLE 설정으로 컨텍스트가 정상 기동한다")
    void contextLoads() {
    }

    @Test
    @DisplayName("MCP 도구 빈 4종(도구 5개)이 모두 등록된다")
    void mcp도구빈_4종_등록() {
        assertThat(context.getBean(ProjectMcpTools.class)).isNotNull();
        assertThat(context.getBean(UtilizationMcpTools.class)).isNotNull();
        assertThat(context.getBean(IdentityMcpTools.class)).isNotNull();
        assertThat(context.getBean(MaintenanceMcpTools.class)).isNotNull();
    }
}
