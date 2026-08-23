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
import kr.proten.pms.audit.AuditQueryService;
import kr.proten.pms.audit.AuditRecord;
import kr.proten.pms.audit.AuditSource;
import kr.proten.pms.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
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
    // 목록 건수를 파서 없이 세는 기준 필드 — 프로젝트 항목마다 정확히 한 번 실린다
    private static final String CLIENT_FIELD = "\\\"client\\\"";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @LocalServerPort
    int port;

    @Autowired
    AuthService authService;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    AuditQueryService auditQueryService;

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

    // --- 유지보수 실연결 (eval C류 앵커) -------------------------------------

    @Test
    @DisplayName("eval C류 — 사용자가 부르는 고객사 이름이 사이트명으로만 계약에 도달한다")
    void searchMaintenance_reachesContractThroughSiteName() {
        String body = mcp.call(accessToken(MEMBER_EMAIL), McpHttp.searchMaintenance("가천대길병원"));

        // 계약명("그룹웨어 유지보수")·계약사("㈜가온아이")에 없는 문자열이다 —
        // 45사이트 계약이 걸린 근거를 matchedSites가 보여 준다
        assertThat(body).contains("가온아이").contains("가천대길병원");
        assertThat(body).doesNotContain(ERROR_FLAG);
        // 화자를 가리지 않는다: 유지보수는 전사 공개다(AC D4-3) — 팀원 토큰으로도 나온다
    }

    @Test
    @DisplayName("eval C류 — search_maintenance가 준 계약 id로 이슈 7건이 열린다 (id 확보 경로)")
    void listMaintenanceLogs_followsContractIdFromSearch() {
        String token = accessToken(ADMIN_EMAIL);
        String found = mcp.call(token, McpHttp.searchMaintenance("한국거래소"));
        int contractId = McpHttp.intFieldOf(found, "contractId");

        String logs = mcp.call(token, McpHttp.listMaintenanceLogs(contractId));

        // 2026-08-11 결정 ④가 뚫으려던 공백이 이 두 줄이다: 도구가 준 id가
        // 다른 도구의 입력으로 실제로 쓰인다
        assertThat(logs).contains("CONTRACT");
        assertThat(logs).contains("한국거래소");
        assertThat(logs).doesNotContain(ERROR_FLAG);
        // 계약 101 앵커 — 이슈 7건(MaintenanceSeedLoadIntegrationTest가 고정한 수)
        assertThat(McpHttp.intFieldOf(logs, "contractId")).isEqualTo(contractId);
        assertThat(countOf(logs, "\\\"receivedAt\\\"")).isEqualTo(7);
    }

    @Test
    @DisplayName("이슈 유형 필터가 목록을 좁힌다 — 라벨은 화면과 같은 한국어다")
    void listMaintenanceLogs_filtersByType() {
        String token = accessToken(ADMIN_EMAIL);
        int contractId = McpHttp.intFieldOf(
                mcp.call(token, McpHttp.searchMaintenance("한국거래소")), "contractId");

        String all = mcp.call(token, McpHttp.listMaintenanceLogs(contractId));
        String filtered = mcp.call(token, McpHttp.listMaintenanceLogs(contractId, "장애"));

        assertThat(filtered).doesNotContain(ERROR_FLAG);
        assertThat(countOf(filtered, "\\\"receivedAt\\\""))
                .isLessThan(countOf(all, "\\\"receivedAt\\\""));
    }

    @Test
    @DisplayName("이슈 id 갈래 — 계약 id 공간과 겹치지 않아 이슈로 해석된다 (eval C-04 전제)")
    void listMaintenanceLogs_readsIssueIdSpace() {
        String token = accessToken(ADMIN_EMAIL);
        int contractId = McpHttp.intFieldOf(
                mcp.call(token, McpHttp.searchMaintenance("한국거래소")), "contractId");
        // 계약의 이슈 목록에서 이슈 id를 얻는다 — 모델이 밟는 경로와 같다
        int issueId = McpHttp.firstIssueIdOf(
                mcp.call(token, McpHttp.listMaintenanceLogs(contractId)));

        String body = mcp.call(token, McpHttp.listMaintenanceLogs(issueId));

        // 이슈 id(230~496)가 계약 id(1~105)에 가려지지 않는다 — PR #25의 시드 id
        // 재부여가 이 단정을 가능하게 했다. eval C-04는 이슈 id 직접 제공형이라
        // 이 갈래가 죽어 있으면 치명 케이스에서 막힌다
        assertThat(body).contains("ISSUE");
        assertThat(body).doesNotContain(ERROR_FLAG);
        assertThat(McpHttp.firstIssueIdOf(body)).isEqualTo(issueId);
        // 이슈 한 건만 실린다
        assertThat(countOf(body, "\\\"receivedAt\\\"")).isEqualTo(1);
    }

    @Test
    @DisplayName("모르는 상태 라벨은 422로 유효 값을 알려 준다 — 조용히 무시하지 않는다")
    void searchMaintenance_rejectsUnknownStatusLabel() {
        String body = mcp.call(accessToken(ADMIN_EMAIL), McpHttp.searchMaintenanceByStatus("진행중"));

        assertThat(body).contains("[422 VALIDATION]").contains("예정/신규/유지/종료");
    }

    @Test
    @DisplayName("없는 id는 404 은닉 정본 문구로 수렴한다 — 계약도 이슈도 아닌 id")
    void listMaintenanceLogs_concealsMissingId() {
        String body = mcp.call(accessToken(ADMIN_EMAIL), McpHttp.listMaintenanceLogs(999_999));

        assertThat(body).contains("[404 NOT_FOUND]").contains("조회 가능한 범위");
    }

    // --- project 실연결 (search_projects · update_progress) --------------------

    @Test
    @DisplayName("프로젝트 가시성 — 같은 키워드가 화자에 따라 다른 목록을 준다 (챗 = 화면)")
    void searchProjects_appliesCallerVisibility() {
        String admin = mcp.call(accessToken(ADMIN_EMAIL), McpHttp.searchProjects("한국타이어"));
        String member = mcp.call(accessToken(MEMBER_EMAIL), McpHttp.searchProjects("한국타이어"));

        // 전사 2건이지만, 완료분 PM은 AX솔루션개발2팀이라 CS사업팀 팀원에게는 없는 것과 같다
        assertThat(countOf(admin, CLIENT_FIELD)).isEqualTo(2);
        assertThat(countOf(member, CLIENT_FIELD)).isEqualTo(1);
    }

    @Test
    @DisplayName("절단 50건 — description이 약속한 수치를 넘기지 않는다 (전사 382건)")
    void searchProjects_truncatesAtFifty() {
        String body = mcp.call(accessToken(ADMIN_EMAIL), McpHttp.SEARCH_PROJECTS);

        // 절단을 문구로 약속하지 않으면 모델이 잘린 개수를 전체로 답한다 —
        // 이 수치와 description은 한 세트다(2026-08-23 결정)
        assertThat(countOf(body, CLIENT_FIELD)).isEqualTo(50);
    }

    @Test
    @DisplayName("상태 필터 — 한국어 라벨이 화면과 같은 표기로 통한다 (eval B-04: 수주확정 19건)")
    void searchProjects_filtersByStatusLabel() {
        String body = mcp.call(accessToken(ADMIN_EMAIL), McpHttp.searchProjectsByStatus("수주확정"));

        assertThat(body).doesNotContain(ERROR_FLAG);
        assertThat(countOf(body, CLIENT_FIELD)).isEqualTo(19);
    }

    @Test
    @DisplayName("모르는 상태 라벨은 422로 유효 값을 알려 준다 — 필터를 조용히 지우지 않는다")
    void searchProjects_rejectsUnknownStatusLabel() {
        // "유지"는 유지보수 계약의 상태다 — 도구를 헷갈린 모델이 실제로 보낼 수 있는 값
        String body = mcp.call(accessToken(ADMIN_EMAIL), McpHttp.searchProjectsByStatus("유지"));

        assertThat(body).contains("[422 VALIDATION]").contains("계약대기");
    }

    @Test
    @DisplayName("keyword는 부분 일치다 — 토큰 AND가 아니다 (목업과의 괴리, 2026-08-23 결정)")
    void searchProjects_matchesSubstringNotTokenAnd() {
        String token = accessToken(ADMIN_EMAIL);

        // 붙어 있으면 찾고, 같은 토큰을 뒤집어 흩어 놓으면 못 찾는다. 목업은 토큰 AND였고
        // eval B-01·B-05는 우연히 둘 다 통과한다("AI 검색"은 solution 값 자체다) —
        // 그래서 문구를 실제 거동에 맞췄고, 그 사실을 이 테스트가 붙잡는다
        assertThat(countOf(mcp.call(token, McpHttp.searchProjects("한국거래소 경영정보시스템")), CLIENT_FIELD))
                .isEqualTo(1);
        assertThat(countOf(mcp.call(token, McpHttp.searchProjects("경영정보시스템 한국거래소")), CLIENT_FIELD))
                .isZero();
        // eval B-01 앵커 — 키워드가 이름이 아니라 솔루션 필드로 도달한다
        assertThat(countOf(mcp.call(token, McpHttp.searchProjects("AI 검색")), CLIENT_FIELD))
                .isEqualTo(17);
    }

    @Test
    @DisplayName("상세 갈래 — version이 함께 온다 (FR-AI-10: 쓰기가 조회만으로 완결)")
    void searchProjects_detailCarriesVersion() {
        String token = accessToken(ADMIN_EMAIL);
        int projectId = McpHttp.firstProjectIdOf(
                mcp.call(token, McpHttp.searchProjects("한국거래소 경영정보시스템")));

        String body = mcp.call(token, McpHttp.projectDetail(projectId));

        assertThat(body).doesNotContain(ERROR_FLAG);
        assertThat(McpHttp.firstProjectIdOf(body)).isEqualTo(projectId);
        assertThat(McpHttp.intFieldOf(body, "version")).isNotNegative();
        // 목록에 없는 상세 전용 필드 — PM 이름·계약 M/M
        assertThat(body).contains("김문수").contains("contractMm");
    }

    @Test
    @DisplayName("가시성 밖 프로젝트는 403이 아니라 404 정본 문구로 숨는다 (AC A3-2)")
    void searchProjects_concealsInvisibleProject() {
        // 관리자에게는 보이는 프로젝트를 CS사업팀 팀원에게 물어본다 — 부재와 같은 문구여야 한다
        int projectId = McpHttp.firstProjectIdOf(
                mcp.call(accessToken(ADMIN_EMAIL), McpHttp.searchProjects("한미글로벌 프로젝트 데이터")));

        String body = mcp.call(accessToken(MEMBER_EMAIL), McpHttp.projectDetail(projectId));

        assertThat(body).contains("[404 NOT_FOUND]").contains("조회 가능한 범위");
    }

    @Test
    @DisplayName("2단계 확인 1단계 — confirmed=false는 요약만 주고 DB를 바꾸지 않는다 (구조 원칙 5)")
    void updateProgress_summaryDoesNotCommit() {
        String token = accessToken(MEMBER_EMAIL);
        // 남진식은 이 프로젝트의 참여자다 — 배정 인원이면 역할 무관하게 가능하다
        int projectId = McpHttp.firstProjectIdOf(
                mcp.call(token, McpHttp.searchProjects("한국거래소 경영정보시스템")));
        String detail = mcp.call(token, McpHttp.projectDetail(projectId));
        int before = McpHttp.intFieldOf(detail, "progress");

        String summary = mcp.call(token, McpHttp.updateProgress(
                projectId, 40, McpHttp.intFieldOf(detail, "version"), false));

        assertThat(summary).doesNotContain(ERROR_FLAG);
        // 확인 카드의 재료 — 현재값 → 새값 (eval D-01 채점 기준)
        assertThat(McpHttp.intFieldOf(summary, "previousProgress")).isEqualTo(before);
        assertThat(McpHttp.intFieldOf(summary, "requestedProgress")).isEqualTo(40);
        // DB는 그대로다
        assertThat(McpHttp.intFieldOf(mcp.call(token, McpHttp.projectDetail(projectId)), "progress"))
                .isEqualTo(before);
    }

    @Test
    @DisplayName("2단계 확인 2단계 — confirmed=true가 저장하고 감사에 source=MCP로 남는다")
    void updateProgress_commitsAndRecordsMcpSource() {
        String token = accessToken(MEMBER_EMAIL);
        int projectId = McpHttp.firstProjectIdOf(
                mcp.call(token, McpHttp.searchProjects("한국거래소 경영정보시스템")));
        String detail = mcp.call(token, McpHttp.projectDetail(projectId));

        String result = mcp.call(token, McpHttp.updateProgress(
                projectId, 95, McpHttp.intFieldOf(detail, "version"), true));

        assertThat(result).doesNotContain(ERROR_FLAG);
        assertThat(McpHttp.intFieldOf(mcp.call(token, McpHttp.projectDetail(projectId)), "progress"))
                .isEqualTo(95);
        // 감사 출처는 어댑터가 배선하지 않는다 — AuditSourceResolver가 /mcp 경로로 판정하므로
        // 쓰기 도구가 처음 실연결된 지금이 그 판정을 실측할 수 있는 첫 시점이다
        assertThat(auditQueryService.findByProject(projectId, PageRequest.of(0, 1)).getContent())
                .singleElement()
                .extracting(AuditRecord::source)
                .isEqualTo(AuditSource.MCP);
    }

    @Test
    @DisplayName("낙관적 락 — 틀린 version은 409로 거절되고 자동 재시도를 금지한다")
    void updateProgress_rejectsStaleVersion() {
        String token = accessToken(MEMBER_EMAIL);
        int projectId = McpHttp.firstProjectIdOf(
                mcp.call(token, McpHttp.searchProjects("한국거래소 경영정보시스템")));
        int version = McpHttp.intFieldOf(
                mcp.call(token, McpHttp.projectDetail(projectId)), "version");

        String body = mcp.call(token, McpHttp.updateProgress(projectId, 95, version + 7, true));

        assertThat(body).contains("[409 STALE_VERSION]").contains("자동 재시도");
    }

    @Test
    @DisplayName("보이지만 담당자 아님 — 403이며 404 은닉과 구분된다 (eval E-01형)")
    void updateProgress_rejectsNonAssignee() {
        // 같은 CS사업팀 팀장의 프로젝트라 보이지만, 남진식은 배정되지 않았다
        String token = accessToken(MEMBER_EMAIL);
        int projectId = McpHttp.firstProjectIdOf(
                mcp.call(token, McpHttp.searchProjects("한국타이어 TRAMA")));

        // 1단계에서 이미 거절된다 — 권한 판정이 confirmed 분기보다 앞이다
        String body = mcp.call(token, McpHttp.updateProgress(projectId, 80, 0, false));

        assertThat(body).contains("[403 FORBIDDEN]");
    }

    @Test
    @DisplayName("미구현 포트는 FR-AI-26 표준 오류를 반환한다 — 도구는 노출된 채로")
    void unimplementedPort_returnsStandardUnavailableError() {
        // 남은 503은 가동률 2종뿐이다 — EPIC C 실구현에 묶여 있다
        String body = mcp.call(accessToken(ADMIN_EMAIL), McpHttp.GET_UTILIZATION);

        assertThat(body).contains("[503 UNAVAILABLE]").contains("준비 중");
    }

    /** 응답에 그 필드가 몇 번 실렸는가 — 이슈 건수를 파서 없이 센다. */
    private static int countOf(String body, String token) {
        return body.split(java.util.regex.Pattern.quote(token), -1).length - 1;
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
