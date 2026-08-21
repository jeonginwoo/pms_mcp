package kr.proten.pms.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 게이트 M0 인증 3케이스 (구현_노트 §1-4) — pms-mcp-mock B2-2 테스트의 승격.
 * ① 무토큰 401 ② 타 audience 401 ③ 정상 토큰 → whoami가 그 사용자 반환(시드 실데이터).
 * 추가: 위조 서명 401 · 화자 전환 · 카탈로그 8종 노출 · 인력 가시성 ·
 * 미구현 포트의 FR-AI-26 표준 오류 · refresh형 token_type 거절.
 * 가시성 E2E 전체(프로젝트·404 은닉)는 각 모듈 서비스가 port를 구현할 때
 * mock 테스트에서 마저 승격한다(PMS-M1). JWKS 모드는 McpJwksAuthTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpJwtAuthTest {

    @LocalServerPort
    int port;

    @Value("${pms.auth.hs256-secret}")
    String secret;

    McpHttp mcp;

    @BeforeEach
    void setUp() {
        mcp = new McpHttp(port);
    }

    // --- 게이트 M0 인증 3케이스 ---------------------------------------------

    @Test
    void 무토큰_401() {
        assertThat(mcp.post(McpHttp.INITIALIZE, null, Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    void 타_audience_401() {
        String wrongAud = TestJwt.mint(secret, 18, "전세아", "other-service");
        assertThat(mcp.post(McpHttp.INITIALIZE, wrongAud, Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    void 정상_토큰_whoami가_토큰_사용자를_반환() {
        String body = mcp.call(TestJwt.mint(secret, 18, "전세아", "pms"), McpHttp.WHOAMI);
        assertThat(body).contains("전세아").contains("팀원");
        assertThat(body).doesNotContain("isError\":true");
    }

    // --- 추가 방어선 (B2-2 승격) --------------------------------------------

    @Test
    void 위조_서명_401() {
        String forged = TestJwt.mint("wrong-secret-wrong-secret-wrong-secret-000000", 18, "전세아", "pms");
        assertThat(mcp.post(McpHttp.INITIALIZE, forged, Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    void refresh형_token_type은_401() {
        // 장수명 refresh 토큰(aud=pms·서명 정상)의 /mcp 오용 차단 — 허용 목록 밖 유형
        String refresh = TestJwt.mint(secret, 18, "전세아", "pms", "refresh");
        assertThat(mcp.post(McpHttp.INITIALIZE, refresh, Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    void 화자_전환_토큰별로_caller가_달라진다() {
        String admin = mcp.call(TestJwt.mint(secret, 1, "신현랑", "pms"), McpHttp.WHOAMI);
        assertThat(admin).contains("신현랑").doesNotContain("전세아");
    }

    @Test
    void 카탈로그_8종이_그대로_노출된다() {
        String body = mcp.call(TestJwt.mint(secret, 18, "전세아", "pms"), McpHttp.TOOLS_LIST);
        assertThat(body).contains("whoami", "find_person", "search_projects", "get_utilization",
                "list_overbooked", "search_maintenance", "list_maintenance_logs", "update_progress");
    }

    @Test
    void 인력_가시성_팀원은_본인만_관리자는_전사() {
        // 18 전세아(팀원 그룹=본인 가시성) vs 1 신현랑(관리자 그룹=전사)
        String member = mcp.call(TestJwt.mint(secret, 18, "전세아", "pms"), McpHttp.FIND_ALL_PEOPLE);
        assertThat(member).contains("전세아").doesNotContain("신현랑");
        String admin = mcp.call(TestJwt.mint(secret, 1, "신현랑", "pms"), McpHttp.FIND_ALL_PEOPLE);
        assertThat(admin).contains("신현랑").contains("전세아");
    }

    @Test
    void 미구현_포트는_준비_중_표준_오류를_반환한다() {
        // FR-AI-26 — 도구는 노출하되 실패 사실·재시도 가능 여부를 표준 형식으로
        String body = mcp.call(TestJwt.mint(secret, 18, "전세아", "pms"), McpHttp.SEARCH_PROJECTS);
        assertThat(body).contains("[503 UNAVAILABLE]").contains("준비 중");
    }
}
