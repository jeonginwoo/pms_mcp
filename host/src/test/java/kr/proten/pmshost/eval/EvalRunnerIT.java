package kr.proten.pmshost.eval;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import kr.proten.pmshost.chat.ChatService;
import kr.proten.pmshost.chat.SystemPrompts;
import kr.proten.pmshost.mcp.PmsMcpConnector;
import kr.proten.pmshost.support.SeedLogin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>eval 자동 실행 장치</b> — 36케이스를 실 LLM·실 서버로 밟고 회차 기록을 남긴다.
 * 채점은 하지 않는다(다음 항목): 여기서 만드는 것은 <b>채점 가능한 물증</b>이다 —
 * 발화·응답·도구 흐름·인자·지연, 그리고 쓰기 케이스의 DB 결과.
 *
 * <p>기본은 스킵이다. 실 LLM 비용이 들고 DB를 바꾸므로 켜야만 돈다:
 * <pre>
 *   bash host/scripts/eval-run.sh                # DB 초기화 → pms 기동 → 전량 실행
 *   (cd host &amp;&amp; ./gradlew test -Deval.run=true -Deval.only=A-05,C-04,H-01)
 * </pre>
 *
 * <p><b>왜 배포된 8081이 아니라 in-process인가.</b> 채점에 필요한 것은 도구 인자까지의
 * 흐름인데, 밖에서는 로그 파일을 잘라 케이스 경계를 시간으로 맞춰야 한다. 같은 빈·같은
 * 프롬프트·같은 MCP 클라이언트를 그대로 쓰되 관측만 안에서 한다({@link TurnObserver}).
 * `/chat` 컨트롤러가 하는 일은 토큰 형식 검사와 conversationId 기본값뿐이라(그 자리는
 * 실전에서 BFF가 대신한다) 채점 대상 밖이다.
 *
 * <p><b>순서가 의미를 갖는다.</b> 원장 순서로 돈다 — B-02가 프로젝트 347의 "현재 5%"를
 * 채점하는데 D류가 같은 프로젝트에 쓰기 때문이다. D류 안에서는 케이스마다 시드
 * 기준선으로 되돌린다({@link EvalFixtures}) — 원장의 기대값이 단독 실행 기준이라서다.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "eval.run", matches = "true")
class EvalRunnerIT {

    private static final Logger log = LoggerFactory.getLogger(EvalRunnerIT.class);

    private static final String PMS = "http://localhost:8080";
    private static final Path RESULTS = Path.of("..", "docs", "evals", "results");

    /** 시드 적재 정본 건수 — 빈 DB에 36케이스를 태우면 전량 오답이 된다 */
    private static final int SEEDED_PROJECTS = 382;
    private static final int SEEDED_CONTRACTS = 105;

    /** 대화 체인을 되짚을 때 쓰는 원장 색인 — 케이스마다 json을 다시 읽지 않는다 */
    private static final Map<String, EvalCases.Case> LEDGER_BY_ID = ledgerById();

    // null을 지우지 않는다 — 기록에서 `"reply":null`은 "빈 답"이 아니라
    // "그 턴이 실패했다"는 증거다. 지우면 채점자가 그것을 볼 수 없다.
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    private ChatService chat;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private Clock clock;

    @Value("${spring.ai.anthropic.chat.model}")
    private String model;

    @Test
    @DisplayName("eval 36케이스를 원장 순서로 실행하고 회차 기록을 남긴다")
    void runLedger() throws Exception {
        List<EvalCases.Case> cases = selected();
        String runId = LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        Path outDir = RESULTS.resolve(runId);
        Files.createDirectories(outDir);

        try (EvalFixtures fixtures = new EvalFixtures(); TurnObserver observer = new TurnObserver()) {
            requireSeedBaseline(fixtures);
            fixtures.snapshot(writeTargets(cases));

            List<Map<String, Object>> records = new ArrayList<>();
            for (EvalCases.Case c : cases) {
                log.info("[eval] {} — 화자 {}({})", c.id(), c.speaker().name(), c.speaker().personId());
                records.add(runCase(c, fixtures, observer));
            }

            writeTranscript(outDir, records);
            writeSummary(outDir, runId, cases, records);
            log.info("[eval] 회차 기록: {}", outDir.toAbsolutePath().normalize());

            assertThat(records).as("케이스가 하나도 실행되지 않았다").isNotEmpty();
        }
    }

    // --- 한 케이스 -------------------------------------------------------------

    private Map<String, Object> runCase(EvalCases.Case c, EvalFixtures fixtures,
            TurnObserver observer) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", c.id());
        record.put("speaker", Map.of("personId", c.speaker().personId(), "name", c.speaker().name()));
        record.put("conversation", conversationOf(c));
        if (c.inject() != null) {
            record.put("inject", c.inject().kind());
        }

        fixtures.restore(c.restore());
        if (c.inject() != null && "contaminate".equals(c.inject().kind())) {
            fixtures.contaminate(c.inject().issueId(), c.inject().authorId(), c.inject().text());
        }
        observer.drain();
        observer.drainEmptyReplies();

        List<Map<String, Object>> turns = new ArrayList<>();
        try (FaultWiring fault = FaultWiring.forCase(c, chatClientBuilder, clock)) {
            ChatService service = fault.service(chat);
            for (int i = 0; i < c.turns().size(); i++) {
                if (c.inject() != null && "concurrentWrite".equals(c.inject().kind())
                        && c.inject().beforeTurn() == i + 1) {
                    fixtures.concurrentWrite(c.inject().projectId(), c.inject().progress());
                    turns.add(Map.of("n", i + 1, "injected",
                            "다른 사용자가 프로젝트 %d을(를) %d%%로 수정 (version +1)"
                                    .formatted(c.inject().projectId(), c.inject().progress())));
                }
                turns.add(runTurn(c, i, service, observer));
            }
        } catch (Exception e) {
            // 한 케이스의 실패로 회차 전체를 잃지 않는다 — 기록하고 다음으로 간다
            record.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("[eval] {} 실행 실패", c.id(), e);
        }
        record.put("turns", turns);

        List<EvalFixtures.ProjectState> after = c.restore().stream()
                .map(fixtures::projectState).filter(Objects::nonNull).toList();
        if (!after.isEmpty()) {
            record.put("dbAfter", after);
        }

        return record;
    }

    private Map<String, Object> runTurn(EvalCases.Case c, int index, ChatService service,
            TurnObserver observer) {
        String message = c.turns().get(index);
        // 토큰은 턴마다 새로 받는다 — pms가 재기동하면 앞서 받은 토큰이 전부 죽는다
        String token = SeedLogin.accessToken(PMS, c.speaker().personId());

        long startedAt = System.nanoTime();
        String reply;
        String error = null;
        try {
            reply = service.chat(conversationOf(c), message, token);
        } catch (Exception e) {
            reply = null;
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("[eval] {} 턴 {} 실패", c.id(), index + 1, e);
        }
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        Map<String, Object> turn = new LinkedHashMap<>();
        turn.put("n", index + 1);
        turn.put("user", message);
        turn.put("reply", reply);
        turn.put("elapsedMs", elapsedMs);
        TurnObserver.Evidence evidence = observer.drain();
        turn.put("toolCalls", evidence.calls());
        // 도구 결과까지 남긴다 — F1(수치 환각)은 "답에 있는 수치가 도구 결과에 있는가"로
        // 판정되므로, 결과가 없으면 채점층이 케이스별 기대값 사본을 만들어야 한다
        turn.put("toolResults", evidence.results());
        List<String> empties = observer.drainEmptyReplies();
        if (!empties.isEmpty()) {
            // 모델의 회피성 무응답(F6)과 host의 본문 유실을 채점자가 갈라 보게 한다
            turn.put("hostEmptyReply", empties);
        }
        if (error != null) {
            turn.put("error", error);
        }

        return turn;
    }

    /** 대화 체인 — `continues`가 걸린 케이스는 앞 케이스의 대화를 그대로 잇는다 */
    private static String conversationOf(EvalCases.Case c) {
        Map<String, EvalCases.Case> byId = LEDGER_BY_ID;

        EvalCases.Case cursor = c;
        Set<String> seen = new HashSet<>();
        while (cursor.continues() != null && seen.add(cursor.id())) {
            EvalCases.Case parent = byId.get(cursor.continues());
            if (parent == null) {
                throw new IllegalStateException(cursor.id() + "가 잇는다는 " + cursor.continues()
                        + " 케이스가 없다");
            }
            if (parent.speaker().personId() != c.speaker().personId()) {
                // 대화 메모리 키가 화자(sub)로 갈리므로 화자가 다르면 맥락이 이어지지 않는다
                throw new IllegalStateException(
                        c.id() + "와 " + parent.id() + "의 화자가 다르다 — 대화를 이을 수 없다");
            }
            cursor = parent;
        }

        return cursor.id();
    }

    // --- 선행 조건 -------------------------------------------------------------

    /**
     * <b>시드 기준선 확인.</b> 되돌리기의 기준값을 러너가 시작 시점 DB에서 읽으므로,
     * 오염된 DB로 시작하면 오염값이 "시드"가 되어 조용히 틀린다.
     *
     * <p>증거는 `audit_logs`다 — 시드 로더는 감사 행을 남기지 않으므로(2026-08-24 실측)
     * 0행이 아니라는 것은 누군가 이미 썼다는 뜻이다. 진척률 값을 일일이 대조하는 것보다
     * 이 한 줄이 낫다: 앵커 수치를 코드에 복제하지 않고도 같은 것을 증명한다.
     */
    private static void requireSeedBaseline(EvalFixtures fixtures) {
        assertThat(fixtures.count("projects"))
                .as("시드 프로젝트가 적재돼 있어야 한다 — pms를 빈 DB로 기동했는가?")
                .isEqualTo(SEEDED_PROJECTS);
        assertThat(fixtures.count("maintenance_contracts"))
                .as("시드 유지보수 계약이 적재돼 있어야 한다")
                .isEqualTo(SEEDED_CONTRACTS);
        assertThat(fixtures.count("audit_logs"))
                .as("DB가 시드 직후 상태여야 한다 — 감사 행이 있으면 이미 쓰기가 일어났다. "
                        + "compose 볼륨을 지우고 다시 적재하라(host/scripts/eval-run.sh)")
                .isZero();
    }

    private static Map<String, EvalCases.Case> ledgerById() {
        Map<String, EvalCases.Case> byId = new LinkedHashMap<>();
        EvalCases.load().forEach(each -> byId.put(each.id(), each));

        return Map.copyOf(byId);
    }

    /** 되돌리기 대상 = 케이스들이 선언한 restore 집합. 시작 시점에 한 번 스냅샷한다. */
    private static List<Long> writeTargets(List<EvalCases.Case> cases) {
        Set<Long> ids = new LinkedHashSet<>();
        cases.forEach(c -> ids.addAll(c.restore()));

        return List.copyOf(ids);
    }

    /** `-Deval.only=A-05,C-04` — 장치를 확인할 때 쓰는 부분 실행. 순서는 원장 순서 그대로. */
    private static List<EvalCases.Case> selected() {
        String only = System.getProperty("eval.only", "").trim();
        List<EvalCases.Case> all = EvalCases.load();
        if (only.isEmpty()) {
            return all;
        }
        Set<String> wanted = new LinkedHashSet<>(List.of(only.split("\\s*,\\s*")));
        List<EvalCases.Case> picked = all.stream().filter(c -> wanted.contains(c.id())).toList();
        assertThat(picked).as("-Deval.only가 가리키는 케이스가 원장에 없다: %s", only).isNotEmpty();

        return picked;
    }

    // --- 회차 기록 -------------------------------------------------------------

    private void writeTranscript(Path outDir, List<Map<String, Object>> records) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> record : records) {
            sb.append(json.writeValueAsString(record)).append('\n');
        }
        Files.writeString(outDir.resolve("transcript.jsonl"), sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * 사람이 읽는 판. 채점자가 보는 것은 결국 <b>발화 → 도구 흐름 → 응답</b> 셋이므로
     * 그 셋을 한 화면에 붙여 둔다. 회귀 규칙(PRD-host §6-3)이 "결과는 버전과 함께
     * 기록"을 요구하므로 모델명과 <b>프롬프트 지문</b>을 머리에 박는다 — 프롬프트가
     * 1자라도 바뀌면 지문이 달라져 다른 회차임이 드러난다.
     */
    private void writeSummary(Path outDir, String runId, List<EvalCases.Case> cases,
            List<Map<String, Object>> records) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("# eval 실행 기록 — ").append(runId).append("\n\n");
        sb.append("| 항목 | 값 |\n|---|---|\n");
        long broken = records.stream().filter(EvalRunnerIT::isBroken).count();
        sb.append("| 실행 케이스 | ").append(records.size() - broken).append(" / 36");
        if (broken > 0) {
            // 시도 수만 적으면 끊긴 회차가 온전한 회차처럼 보인다 — 2026-08-25 크레딧
            // 소진으로 16건이 빈 응답이 됐는데 머리표는 36/36이었다
            sb.append(" — **").append(broken).append("건 손상(오류·빈 응답). 게이트 입력이 될 수 없다**");
        }
        sb.append(" |\n");
        sb.append("| 모델 | `").append(model).append("` |\n");
        sb.append("| 시스템 프롬프트 | ").append(promptVersion())
                .append(" (지문 `").append(promptFingerprint()).append("`) |\n");
        sb.append("| pms | ").append(PMS).append(" |\n");
        sb.append("| 기준일 | ").append(java.time.LocalDate.now(clock)).append(" |\n");
        sb.append("| 기대값 정본 | `docs/evals/eval-cases.md` · `docs/evals/seed-anchor-map.md` |\n\n");
        sb.append("> 채점 기록이 아니다 — 판정은 원장의 합격 기준으로 따로 매긴다.\n\n");

        for (int i = 0; i < records.size(); i++) {
            Map<String, Object> record = records.get(i);
            EvalCases.Case c = cases.get(i);
            sb.append("## ").append(record.get("id")).append(" — ")
                    .append(c.speaker().name()).append('(').append(c.speaker().personId()).append(')');
            if (record.containsKey("inject")) {
                sb.append(" · 주입 `").append(record.get("inject")).append('`');
            }
            if (!conversationOf(c).equals(c.id())) {
                sb.append(" · 대화 `").append(conversationOf(c)).append("` 이어받음");
            }
            sb.append("\n\n");

            for (Object each : (List<?>) record.get("turns")) {
                Map<?, ?> turn = (Map<?, ?>) each;
                if (turn.containsKey("injected")) {
                    sb.append("- **[주입]** ").append(turn.get("injected")).append("\n\n");
                    continue;
                }
                sb.append("**T").append(turn.get("n")).append(" 사용자** ")
                        .append(turn.get("user")).append("\n\n");
                sb.append("- 도구 흐름: ").append(toolFlow(turn)).append('\n');
                sb.append("- 지연: ").append(turn.get("elapsedMs")).append("ms\n");
                if (turn.containsKey("hostEmptyReply")) {
                    sb.append("- **host EMPTY_REPLY** — 모델 무응답이 아니라 본문 유실이다\n");
                }
                if (turn.containsKey("error")) {
                    sb.append("- **오류** ").append(turn.get("error")).append('\n');
                }
                sb.append("\n> ").append(String.valueOf(turn.get("reply"))
                        .replace("\n", "\n> ")).append("\n\n");
            }
            if (record.containsKey("dbAfter")) {
                sb.append("- DB 결과: ").append(json.writeValueAsString(record.get("dbAfter")))
                        .append('\n').append('\n');
            }
            if (record.containsKey("error")) {
                sb.append("- **케이스 오류** ").append(record.get("error")).append("\n\n");
            }
        }

        Files.writeString(outDir.resolve("summary.md"), sb.toString(), StandardCharsets.UTF_8);
    }

    private static String toolFlow(Map<?, ?> turn) {
        List<?> calls = (List<?>) turn.get("toolCalls");
        if (calls == null || calls.isEmpty()) {
            return "(호출 없음)";
        }
        List<String> rendered = new ArrayList<>();
        for (Object call : calls) {
            TurnObserver.ToolCall tc = (TurnObserver.ToolCall) call;
            rendered.add(tc.name() == null ? "`" + tc.raw() + "`"
                    : "`" + tc.name() + "(" + tc.arguments() + ")`");
        }

        return String.join(" → ", rendered);
    }

    /** 오류로 끝났거나 답이 한 턴도 나오지 않은 케이스 — 채점 대상이 아니다 */
    @SuppressWarnings("unchecked")
    private static boolean isBroken(Map<String, Object> record) {
        if (record.get("error") != null) {
            return true;
        }
        var turns = (List<Map<String, Object>>) record.get("turns");
        if (turns == null) {
            return true;
        }

        // 주입 표시 턴(`{n, injected}`)은 발화도 응답도 없는 것이 정상이다 — 이것을
        // 손상으로 세면 주입이 걸린 케이스가 전부 손상으로 잡힌다(D-03·C-04·H-01)
        return turns.stream()
                .filter(turn -> turn.get("user") != null)
                .anyMatch(turn -> ((String) turn.getOrDefault("reply", "")).isBlank());
    }

    /**
     * 프롬프트 <b>버전 라벨</b>은 정본(구현_노트 §4-1 머리)에서 읽는다 — 라벨을 코드에
     * 박아 두면 프롬프트를 고쳐도 라벨이 따라오지 않는다. 실제로 2026-08-25 v0.3 회차가
     * "v0.2"로 기록됐다(지문만 달라져 있었다). 지문이 진실이고 라벨은 사람이 읽는 이름이라,
     * 둘이 갈리면 기록을 믿을 수 없게 된다.
     */
    private static String promptVersion() throws Exception {
        String doc = java.nio.file.Files.readString(Path.of("..", "docs", "구현_노트.md"));
        Matcher m = Pattern.compile("### 4-1\\.\\s*전문\\s*(v[0-9.]+)").matcher(doc);

        return m.find() ? m.group(1) : "(버전 미상 — 구현_노트 §4-1 머리 확인)";
    }

    /** 프롬프트 1자 변경도 드러나는 짧은 지문 (회귀 규칙의 실체) */
    private static String promptFingerprint() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(SystemPrompts.PMS_ASSISTANT.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            hex.append("%02x".formatted(digest[i]));
        }

        return hex.toString();
    }

    // --- H-01 오류 주입 배선 -----------------------------------------------------

    /**
     * 오류 케이스만 <b>다른 MCP 주소를 보는 ChatService</b>로 돌린다.
     * {@link PmsMcpConnector}가 base-url을 생성자로 받으므로 프로덕션 코드를 건드리지
     * 않고 배선만 바꿔 끼울 수 있다 — 앱에 "고장 모드" 스위치를 만들면 그 스위치가
     * 배포본에도 실린다.
     */
    private record FaultWiring(FaultyMcpServer server, ChatService faulty) implements AutoCloseable {

        static FaultWiring forCase(EvalCases.Case c, ChatClient.Builder builder, Clock clock) {
            if (!c.hasFault()) {
                return new FaultWiring(null, null);
            }
            // 카탈로그는 실 서버에서 받아 온다 — 도구 문구를 여기 복제하지 않는다
            var connector = new PmsMcpConnector(PMS);
            io.modelcontextprotocol.spec.McpSchema.ListToolsResult catalog;
            String protocolVersion;
            try (McpSyncClient client = connector.connect(
                    SeedLogin.accessToken(PMS, c.speaker().personId()))) {
                catalog = client.listTools();
                // 실 서버가 합의한 버전을 그대로 쓴다 — 여기서 버전을 지어내면
                // 클라이언트가 거절하거나, 실서버와 다른 조건에서 채점하게 된다
                protocolVersion = client.getCurrentInitializationResult().protocolVersion();
            }
            FaultyMcpServer server = new FaultyMcpServer(catalog, protocolVersion);

            return new FaultWiring(server,
                    new ChatService(builder, new PmsMcpConnector(server.baseUrl()), clock));
        }

        ChatService service(ChatService normal) {
            return faulty == null ? normal : faulty;
        }

        @Override
        public void close() {
            if (server != null) {
                server.close();
            }
        }
    }
}
