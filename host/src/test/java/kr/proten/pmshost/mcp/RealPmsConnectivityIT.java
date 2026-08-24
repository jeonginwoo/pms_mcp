package kr.proten.pmshost.mcp;

import java.util.Map;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.McpHttpClientTransportAuthorizationException;
import io.modelcontextprotocol.spec.McpSchema;
import kr.proten.pmshost.support.SeedLogin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 실 서버 관통 검증(LLM 제외 구간) — 기동 중인 `pms` 앱(8080)의 embedded /mcp에 붙는다.
 * 2026-08-24 실서버 전환분. 기본은 스킵:
 *   docker compose -f pms/docker-compose.yml up -d && (cd pms && ./gradlew bootRun)
 *   (cd host && ./gradlew test -Dpms.real.e2e=true)
 *
 * **`pms` 안의 통합 테스트와 보는 것이 다르다.** 저쪽은 서버 안에서 도구를 부르고,
 * 이건 **host의 MCP 클라이언트·Streamable HTTP 전송·토큰 패스스루가 실 서버에
 * 닿는지**를 본다 — 목업에서만 통했을 수 있는 배선이 여기서 갈린다.
 *
 * 토큰은 목업 IT가 목업 yml에서 시크릿을 읽는 것과 같은 규율로 얻는다:
 * **시드 정본에서 email을 읽어 실제로 로그인한다**(원칙 4 — `/mcp`는
 * `pms.auth.enabled`와 무관하게 로그인 access 토큰만 받는다). 비밀번호는 시드가
 * 전원 공용으로 박아 둔 초기값이라 여기서도 그 상수를 쓴다 — email과 달리 파일에서
 * 뽑을 자리가 없다(해시만 있다).
 */
@EnabledIfSystemProperty(named = "pms.real.e2e", matches = "true")
class RealPmsConnectivityIT {

    private static final String PMS = "http://localhost:8080";

    /** 고예림(19) — eval A-05의 화자. 앵커 정본 §4: 2026-08 기본 63% · 보정 50.4%(계수 0.8) */
    private static final long GO_YERIM = 19;

    @Test
    @DisplayName("로그인 토큰으로 initialize→tools/list(8종)→whoami가 그 사용자를 반환")
    void connectsWithLoginTokenAndResolvesCaller() {
        PmsMcpConnector connector = new PmsMcpConnector(PMS);

        try (McpSyncClient client = connector.connect(accessToken(GO_YERIM))) {
            var tools = client.listTools().tools();
            assertThat(tools).hasSize(8);
            assertThat(tools).extracting(McpSchema.Tool::name).contains("whoami", "search_projects",
                    "get_utilization", "list_overbooked", "find_person", "search_maintenance",
                    "list_maintenance_logs", "update_progress");

            // 화자는 토큰 sub에서만 온다 — 실 토큰에는 name 클레임이 없으므로
            // 이름은 서버가 데이터로 해석한 것이다(원칙 4)
            McpSchema.CallToolResult who = client.callTool(
                    new McpSchema.CallToolRequest("whoami", Map.of()));
            assertThat(who.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(who.content().toString())
                    .contains("고예림").contains("AX솔루션개발1팀").contains("AX솔루션사업부");
        }
    }

    @Test
    @DisplayName("get_utilization(scope=ME)이 앵커 정본 수치를 그대로 준다 — 목업이 아닌 실 데이터")
    void readsAnchorNumbersFromRealData() {
        PmsMcpConnector connector = new PmsMcpConnector(PMS);

        try (McpSyncClient client = connector.connect(accessToken(GO_YERIM))) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "get_utilization", Map.of("month", "2026-08", "scope", "ME")));

            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            String body = result.content().toString();
            assertThat(body).contains("\"basicPct\":63.0").contains("\"adjustedPct\":50.4");
            // scope=ME는 personId 없이 완결된다(2026-07-31 결정) — 본인 1행뿐
            assertThat(body).contains("고예림").doesNotContain("김경민");
        }
    }

    /**
     * eval B-01의 도구 관통. 카탈로그 문구는 keyword가 **이름·고객사·솔루션**을
     * 본다고 말하고 도메인 질의도 셋을 다 걸지만, 실측은 그 문구대로 나온다는 것을
     * 여기서 고정한다 — 2026-08-24 실 LLM 관통에서 이 케이스가 기대 6건에 못 미쳐
     * 원인을 데이터·질의·모델 중 어디로 볼지 가려야 했다.
     */
    @Test
    @DisplayName("search_projects의 keyword는 이름뿐 아니라 solution도 본다 (eval B-01 앵커)")
    void keywordMatchesSolutionNotOnlyName() {
        PmsMcpConnector connector = new PmsMcpConnector(PMS);

        // 김문수(16) = AX솔루션사업부 부문장 — eval B-01의 화자
        try (McpSyncClient client = connector.connect(accessToken(16))) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "search_projects", Map.of("keyword", "AI 검색", "status", "진행중")));

            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            String body = result.content().toString();
            // 앵커 6건 = 전사 9건 중 화자 가시분 (eval B-01)
            assertThat(body).contains("한미글로벌").contains("국가독성과학연구소")  // 이름 일치
                    .contains("롯데관광").contains("서울시 인재개발원")            // solution 일치
                    .contains("한국과학창의재단").contains("경찰청");
            assertThat(body.split("\"id\":", -1)).as("가시 6건이어야 한다").hasSize(7);
            // 가시성 밖(AX영업팀·MS사업부)은 섞이지 않는다
            assertThat(body).doesNotContain("사이버다임").doesNotContain("SH서울주택도시개발공사");
        }
    }

    @Test
    @DisplayName("refresh 토큰으로는 /mcp에 붙지 못한다 — 패스스루가 access만 통한다")
    void refreshTokenIsRejected() {
        PmsMcpConnector connector = new PmsMcpConnector(PMS);

        Throwable thrown = catchThrowable(() -> connector.connect(refreshToken(GO_YERIM)));

        // **아무 예외나 통과시키면 안 된다**: `isInstanceOf(Exception.class)`로 두면
        // pms가 꺼져 있을 때의 연결 거부로도 초록이 되고, 그러면 이 테스트는
        // "/mcp가 refresh 토큰을 받아들이는" 회귀를 못 잡는다. SDK는 인증 실패를
        // 전용 예외로 싣는다(상태 코드 문자열은 메시지에 없다 — 실측).
        assertThat(hasAuthorizationFailure(thrown))
                .as("인증 실패로 끊겼어야 한다 — 실제 예외 사슬: %s", thrown)
                .isTrue();
    }

    /** 예외 사슬 어딘가에 전송 계층 인증 실패가 있는지 — 감싸는 층은 고정하지 않는다 */
    private static boolean hasAuthorizationFailure(Throwable thrown) {
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof McpHttpClientTransportAuthorizationException) {
                return true;
            }
        }

        return false;
    }

    // --- 로그인 -------------------------------------------------------------
    // 발급 규율(시드 정본에서 email → 실제 로그인)은 SeedLogin이 소유한다 —
    // eval 러너도 같은 경로로 토큰을 받으므로 두 벌을 두지 않는다.

    private static String accessToken(long personId) {
        return SeedLogin.accessToken(PMS, personId);
    }

    private static String refreshToken(long personId) {
        return SeedLogin.refreshToken(PMS, personId);
    }

}
