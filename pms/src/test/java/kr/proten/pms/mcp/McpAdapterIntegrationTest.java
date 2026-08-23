package kr.proten.pms.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.proten.pms.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * /mcp 어댑터 관통 검증 — 게이트 M0의 인증 3케이스를 재구축된 앱에서 다시 세운다
 * (2026-08-23 재승격. 이전 측정은 `pms-old`의 임시 시드 어댑터 경유였다).
 *
 * `pms.auth.enabled`를 켜지 않는 것이 이 테스트의 핵심 단정이다: 웹은 헤더로 호출자를
 * 받는 개발 편의 상태여도 `/mcp`는 토큰을 요구해야 한다(구조 원칙 4). 스위치를 켜면
 * 그 성질을 확인할 수 없다.
 *
 * 토큰은 실제 발급 경로로 얻는다 — HS256 테스트 시크릿은 재구축과 함께 사라졌고
 * 되살릴 이유가 없다(구현_노트 B-3의 "JWKS 디코더로 교체"가 이미 끝난 상태).
 *
 * 컨테이너를 따로 띄운다: 시드가 켜진 별도 컨텍스트다
 * (PersonSeedLoadIntegrationTest와 같은 이유).
 */
// JVM 종료 시 "Unsuccessful: drop ..." 로그가 남는다 — 컨테이너가 컨텍스트 캐시보다
// 먼저 내려가는 순서 문제로 무해(일회용 컨테이너라 drop 자체가 불요)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pms.seed.path=../reference/seed")
@Testcontainers
class McpAdapterIntegrationTest {
    // 시드 계정 — 박재완(1) 관리자·전사 가시성 / 남진식(28) 팀원·CS사업팀
    private static final String ADMIN_EMAIL = "pro0001@proten.co.kr";
    private static final String MEMBER_EMAIL = "20230008@proten.co.kr";
    private static final String SEED_PASSWORD = "proten1!";
    // 도구 결과가 오류로 표시됐는지 — SDK가 실패를 이 플래그로 싣는다
    private static final String ERROR_FLAG = "\"isError\":true";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @LocalServerPort
    int port;

    @Autowired
    AuthService authService;

    @Autowired
    JwtEncoder jwtEncoder;

    McpHttp mcp;

    @BeforeEach
    void setUp() {
        mcp = new McpHttp(port);
    }

    // --- 게이트 M0 인증 3케이스 ---------------------------------------------

    @Test
    @DisplayName("무토큰 401 — 인증 스위치가 꺼져 있어도 /mcp는 토큰을 요구한다")
    void withoutToken_isUnauthorized() {
        assertThat(mcp.post(McpHttp.INITIALIZE, null, Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("체인 순서 — /mcp 체인은 자기 경로만 잡는다 (웹 라우트를 삼키지 않는다)")
    void mcpChainMatchesOnlyItsOwnPath() {
        // 프로브는 토큰·호출자 헤더가 모두 불필요한 유일한 라우트다 — /api/people은
        // 인증이 꺼진 동안 헤더로 호출자를 받으므로 헤더 없이는 그 자체로 401이고,
        // 그러면 "체인이 삼켰는지"를 구분하지 못한다
        assertThat(mcp.statusOf("/api/auth/jwks")).isEqualTo(200);
        assertThat(mcp.post(McpHttp.INITIALIZE, null, Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("타 audience 401 — 다른 대상 토큰은 서명이 맞아도 통과하지 못한다")
    void otherAudienceToken_isUnauthorized() {
        String otherService = mint("1", "other-service", "access", jwtEncoder);

        assertThat(mcp.post(McpHttp.INITIALIZE, otherService, Map.of()).statusCode())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("정상 토큰 — whoami가 토큰 subject의 신원을 반환한다 (부문 직속: team=division)")
    void accessToken_whoamiReturnsTokenSubject() {
        String body = mcp.call(accessToken(ADMIN_EMAIL), McpHttp.WHOAMI);

        // 박재완(1) = 경영관리팀 소속이고 그 팀은 회사 root 직계라 부문도 자신이다
        assertThat(body).contains("박재완").contains("경영관리팀").contains("관리자");
        assertThat(body).doesNotContain(ERROR_FLAG);
    }

    @Test
    @DisplayName("화자 전환 — 토큰이 바뀌면 신원도 바뀌고, 팀과 부문이 갈라진다")
    void accessToken_switchesCallerAndResolvesDivision() {
        String body = mcp.call(accessToken(MEMBER_EMAIL), McpHttp.WHOAMI);

        // 남진식(28) = CS사업팀 → 상위 부문 AX솔루션사업부. 승격한 계약이 트리를
        // 실제로 올라갔다는 증거다 — PersonRef.orgUnit 하나로는 나올 수 없는 값이다
        assertThat(body).contains("남진식").contains("CS사업팀").contains("AX솔루션사업부")
                .contains("팀원");
        assertThat(body).doesNotContain("박재완");
    }

    // --- 추가 방어선 ---------------------------------------------------------

    @Test
    @DisplayName("위조 서명 401 — 다른 키로 만든 토큰은 클레임이 맞아도 거절된다")
    void forgedSignature_isUnauthorized() {
        String forged = mint("1", "pms", "access", foreignEncoder());

        assertThat(mcp.post(McpHttp.INITIALIZE, forged, Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("refresh 토큰 401 — 장수명 토큰의 /mcp 오용을 디코더가 막는다")
    void refreshToken_isUnauthorized() {
        String refresh = authService.login(ADMIN_EMAIL, SEED_PASSWORD).refreshToken();

        assertThat(mcp.post(McpHttp.INITIALIZE, refresh, Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("카탈로그 8종이 그대로 노출된다 — 미구현 도메인의 도구도 포함")
    void toolCatalog_exposesAllEight() {
        String body = mcp.call(accessToken(ADMIN_EMAIL), McpHttp.TOOLS_LIST);

        assertThat(body).contains("whoami", "find_person", "search_projects", "get_utilization",
                "list_overbooked", "search_maintenance", "list_maintenance_logs",
                "update_progress");
    }

    @Test
    @DisplayName("인력 가시성 — 팀원은 자기 팀, 관리자는 전사 (챗 = 화면)")
    void findPerson_appliesCallerVisibility() {
        String member = mcp.call(accessToken(MEMBER_EMAIL), McpHttp.FIND_ALL_PEOPLE);
        // 팀원 scope는 TEAM(2026-08-22 결정) — CS사업팀 4명만
        assertThat(member).contains("남진식", "배성수", "김민환", "이은지");
        assertThat(member).doesNotContain("박재완");

        String admin = mcp.call(accessToken(ADMIN_EMAIL), McpHttp.FIND_ALL_PEOPLE);
        assertThat(admin).contains("박재완", "남진식");
        // 시스템 계정은 인력 목록에서 제외된다 — 화면과 같은 규칙
        assertThat(admin).doesNotContain("시스템관리자");
    }

    @Test
    @DisplayName("팀 필터 — 지정한 팀만 남고 가시성은 그대로 적용된다")
    void findPerson_filtersByTeam() {
        String body = mcp.call(accessToken(ADMIN_EMAIL), McpHttp.findPersonByTeam("CS사업팀"));

        assertThat(body).contains("남진식", "배성수");
        assertThat(body).doesNotContain("박재완");
    }

    @Test
    @DisplayName("미구현 포트는 FR-AI-26 표준 오류를 반환한다 — 도구는 노출된 채로")
    void unimplementedPort_returnsStandardUnavailableError() {
        String body = mcp.call(accessToken(ADMIN_EMAIL), McpHttp.SEARCH_PROJECTS);

        assertThat(body).contains("[503 UNAVAILABLE]").contains("준비 중");
    }

    private String accessToken(String email) {
        return authService.login(email, SEED_PASSWORD).accessToken();
    }

    /** 앱의 서명 키가 아닌 키로 서명하는 인코더 — 위조 케이스용. */
    private JwtEncoder foreignEncoder() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            RSAKey key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();

            return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        } catch (Exception e) {
            throw new IllegalStateException("위조 키 생성 실패", e);
        }
    }

    private String mint(String subject, String audience, String tokenType, JwtEncoder encoder) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(now.plus(10, ChronoUnit.MINUTES))
                .claim("token_type", tokenType)
                .build();

        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
}
