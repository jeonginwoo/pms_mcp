package kr.proten.pms.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * JWKS 모드 인증 체인 (구현_노트 §1-1 — JWKS 전환분, 2026-08-18 결정 기록).
 * jwks-uri가 설정되면 HS256보다 항상 우선한다는 정책과, 실 발급 체계(RS256)의
 * 토큰 유형 규칙을 고정한다: refresh(장수명)는 aud=pms·서명이 정상이어도 /mcp
 * 거절 — 허용 = 무클레임(위임 JWT §1-2)·access(로그인)·pat(§1-3), 그 외 기본 거절.
 * JWKS 원천은 테스트 스텁 서버(자체 RSA 키) — 실서버 관통(로그인→/mcp)은
 * 시드 적재 후 게이트 M0 실측과 함께.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpJwksAuthTest {

    // 스텁 JWKS 서버 — 컨텍스트 기동 전에 떠 있어야 하므로 static 초기화
    private static final RSAKey RSA_KEY;
    private static final HttpServer JWKS_SERVER;

    static {
        try {
            RSA_KEY = new RSAKeyGenerator(2048).keyID("mcp-jwks-test").generate();
            byte[] jwks = new JWKSet(RSA_KEY.toPublicJWK()).toString()
                    .getBytes(StandardCharsets.UTF_8);
            JWKS_SERVER = HttpServer.create(new InetSocketAddress(0), 0);
            JWKS_SERVER.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(jwks);
                }
            });
            JWKS_SERVER.start();
        } catch (Exception e) {
            throw new IllegalStateException("JWKS 스텁 서버 기동 실패", e);
        }
    }

    @DynamicPropertySource
    static void jwksUri(DynamicPropertyRegistry registry) {
        // hs256-secret(테스트 yml)은 그대로 둔다 — "설정 시 JWKS 자동 우선"을 그 상태로 검증
        registry.add("pms.auth.jwks-uri",
                () -> "http://localhost:" + JWKS_SERVER.getAddress().getPort() + "/jwks");
    }

    @AfterAll
    static void stopJwksServer() {
        JWKS_SERVER.stop(0);
    }

    @LocalServerPort
    int port;

    @Value("${pms.auth.hs256-secret}")
    String hs256Secret;

    McpHttp mcp;

    @BeforeEach
    void setUp() {
        mcp = new McpHttp(port);
    }

    /** tokenType이 null이면 클레임 미포함 — 위임 JWT(§1-2) 형상 */
    private static String mintRs256(int personId, String audience, String tokenType) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(String.valueOf(personId))
                    .audience(audience)
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(300)));
            if (tokenType != null) {
                claims.claim("token_type", tokenType);
            }
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(RSA_KEY.getKeyID()).build(),
                    claims.build());
            jwt.sign(new RSASSASigner(RSA_KEY));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("RS256 테스트 토큰 서명 실패", e);
        }
    }

    @Test
    void access_토큰이_JWKS_경로로_관통된다() {
        // 로그인 발급형(token_type=access) — sub=18 전세아
        String body = mcp.call(mintRs256(18, "pms", "access"), McpHttp.WHOAMI);
        assertThat(body).contains("전세아");
        assertThat(body).doesNotContain("isError\":true");
    }

    @Test
    void 무클레임_토큰도_통과한다_위임_JWT_형상() {
        // BFF 위임 JWT(§1-2)는 token_type 클레임이 없다 — 기합의 계약 보존 고정
        String body = mcp.call(mintRs256(18, "pms", null), McpHttp.WHOAMI);
        assertThat(body).contains("전세아");
    }

    @Test
    void refresh_토큰_401() {
        // 서명·aud 정상인 장수명 refresh의 /mcp 오용 차단 — 이 세션의 발견 결함 고정
        String refresh = mintRs256(18, "pms", "refresh");
        assertThat(mcp.post(McpHttp.INITIALIZE, refresh, Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    void 미지_token_type_401() {
        // 허용 목록 밖 유형은 기본 거절 — 발급 체계에 새 유형이 생겨도 fail-closed
        String unknown = mintRs256(18, "pms", "password-reset");
        assertThat(mcp.post(McpHttp.INITIALIZE, unknown, Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    void HS256_토큰은_401_JWKS가_항상_우선() {
        // hs256-secret이 설정돼 있어도 jwks-uri가 있으면 HS256 경로는 없다(결정 기록 — 우선순위 고정)
        String hs256 = TestJwt.mint(hs256Secret, 18, "전세아", "pms");
        assertThat(mcp.post(McpHttp.INITIALIZE, hs256, Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    void 타_audience_401_JWKS_경로에서도() {
        String wrongAud = mintRs256(18, "other-service", "access");
        assertThat(mcp.post(McpHttp.INITIALIZE, wrongAud, Map.of()).statusCode()).isEqualTo(401);
    }
}
