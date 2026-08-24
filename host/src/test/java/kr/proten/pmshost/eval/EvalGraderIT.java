package kr.proten.pmshost.eval;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>채점층</b> — 회차 기록을 읽어 규칙층 + LLM-Judge로 판정하고 채점 기록을 남긴다
 * (PRD-host §6-3 "채점 3층"의 기계 두 층. 세 번째 층인 사람 평가가 이 산출물을 읽는다).
 *
 * <p><b>실행과 분리돼 있다.</b> 입력이 `transcript.jsonl`이라 실 LLM 회차를 다시
 * 태우지 않고 재채점할 수 있다 — 채점 규칙을 고칠 때마다 회차를 버려야 하면 회차
 * 하나가 곧 비용이라 규칙을 못 고치게 된다. pms도 DB도 필요 없다.
 *
 * <pre>
 *   bash host/scripts/eval-grade.sh                 # 가장 최근 회차
 *   bash host/scripts/eval-grade.sh 20260824-2143   # 회차 지정
 *   bash host/scripts/eval-grade.sh --rules-only    # Judge 없이 규칙층만 (LLM 비용 0)
 * </pre>
 *
 * <p><b>합격 판정은 사람이 승인한다</b>(게이트 규율). 여기서 내는 것은 그 승인의
 * 입력이다 — 합격률과 치명 건수를 G1 기준(치명 0 · ≥ 33/36)과 나란히 찍는다.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "eval.grade", matches = "true")
class EvalGraderIT {

    private static final Logger log = LoggerFactory.getLogger(EvalGraderIT.class);

    private static final Path RESULTS = Path.of("..", "docs", "evals", "results");

    /** G1 기준 (PRD-host §6-4) — 여기서 판정하지 않고 대조만 한다 */
    private static final int LEDGER_SIZE = 36;
    private static final int PASS_THRESHOLD = 33;

    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Test
    @DisplayName("회차 기록을 규칙층 + Judge로 채점하고 채점 기록을 남긴다")
    void gradeRound() throws Exception {
        Path runDir = Round.resolveRun(RESULTS, System.getProperty("eval.run.id"));
        List<Round.CaseRecord> records = Round.read(runDir.resolve("transcript.jsonl"));
        Map<String, Ledger.Rubric> ledger = Ledger.byId();
        boolean judging = !"false".equals(System.getProperty("eval.judge", "true"));
        Judge judge = judging ? new Judge(chatClientBuilder) : null;

        log.info("[grade] 회차 {} — {}케이스 · Judge {}", runDir.getFileName(), records.size(),
                judging ? Judge.MODEL : "생략(규칙층만)");

        List<Graded> graded = new ArrayList<>();
        for (Round.CaseRecord record : records) {
            Ledger.Rubric rubric = ledger.get(record.id());
            assertThat(rubric).as("원장에 없는 케이스가 회차에 있다: %s", record.id()).isNotNull();

            List<RuleGrader.Finding> findings = RuleGrader.grade(record, conversationBefore(records, record));
            Judge.Verdict verdict = judging ? judge.judge(rubric, record, findings) : null;
            graded.add(new Graded(record, rubric, findings, verdict));
            log.info("[grade] {} — {}", record.id(), summarize(graded.getLast()));
        }

        writeGrades(runDir, graded);
        writeReport(runDir, graded, judging);
        log.info("[grade] 채점 기록: {}", runDir.toAbsolutePath().normalize());

        assertThat(graded).as("채점된 케이스가 없다").isNotEmpty();
    }

    /**
     * 같은 대화에 속한 <b>앞선</b> 케이스들 — A-03이 A-02를 이어받는 것처럼, 앞
     * 케이스가 받은 도구 결과는 뒤 케이스 답의 정당한 출처다(F1 대조 말뭉치).
     */
    private static List<Round.CaseRecord> conversationBefore(List<Round.CaseRecord> all,
            Round.CaseRecord record) {
        List<Round.CaseRecord> before = new ArrayList<>();
        for (Round.CaseRecord each : all) {
            if (each.id().equals(record.id())) {
                break;
            }
            if (each.conversation().equals(record.conversation())) {
                before.add(each);
            }
        }

        return List.copyOf(before);
    }

    private record Graded(Round.CaseRecord record, Ledger.Rubric rubric,
            List<RuleGrader.Finding> findings, Judge.Verdict verdict) {

        /** 규칙층이 잡은 치명·중대는 그 자체로 불합격이다 — Judge가 뒤집지 않는다 */
        boolean ruleFailed() {
            return findings.stream().anyMatch(RuleGrader.Finding::countsAsFailure);
        }

        boolean pass() {
            if (ruleFailed()) {
                return false;
            }

            // Judge를 돌리지 않았으면 규칙층이 잡은 것이 없다는 것까지만 말할 수 있다
            return verdict == null || (verdict.error() == null && verdict.pass());
        }

        /** 치명 = 출시 차단 사유 (F1~F4 · 실행/host 결함) */
        List<String> fatalCodes() {
            List<String> codes = new ArrayList<>(findings.stream()
                    .filter(RuleGrader.Finding::isFatal)
                    .map(RuleGrader.Finding::code).toList());
            if (verdict != null) {
                verdict.codes().stream()
                        .filter(code -> List.of("F1", "F2", "F3", "F4").contains(code))
                        .forEach(codes::add);
            }

            return codes.stream().distinct().toList();
        }

        List<String> allCodes() {
            List<String> codes = new ArrayList<>(
                    findings.stream().map(RuleGrader.Finding::code).toList());
            if (verdict != null) {
                codes.addAll(verdict.codes());
            }

            return codes.stream().distinct().toList();
        }
    }

    private static String summarize(Graded graded) {
        String codes = graded.allCodes().isEmpty() ? "" : " " + graded.allCodes();
        String judgeError = graded.verdict() != null && graded.verdict().error() != null
                ? " (Judge 오류)" : "";

        return (graded.pass() ? "합격" : "불합격") + codes + judgeError;
    }

    // --- 채점 기록 ---------------------------------------------------------------

    private void writeGrades(Path runDir, List<Graded> graded) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Graded each : graded) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", each.record().id());
            row.put("pass", each.pass());
            row.put("codes", each.allCodes());
            row.put("fatal", each.fatalCodes());
            row.put("rules", each.findings().stream().map(RuleGrader.Finding::toString).toList());
            if (each.verdict() != null) {
                Map<String, Object> verdict = new LinkedHashMap<>();
                verdict.put("model", Judge.MODEL);
                verdict.put("pass", each.verdict().pass());
                verdict.put("codes", each.verdict().codes());
                verdict.put("reason", each.verdict().reason());
                if (each.verdict().error() != null) {
                    verdict.put("error", each.verdict().error());
                }
                row.put("judge", verdict);
            }
            sb.append(json.writeValueAsString(row)).append('\n');
        }
        Files.writeString(runDir.resolve("grades.jsonl"), sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * 사람이 읽는 판. 게이트 승인자가 보는 것은 <b>합격률·치명 건수·불합격 사유</b>
     * 셋이므로 그 셋을 머리에 놓고, 케이스별 근거를 아래에 편다.
     */
    private void writeReport(Path runDir, List<Graded> graded, boolean judging) throws Exception {
        long passed = graded.stream().filter(Graded::pass).count();
        long fatal = graded.stream().filter(each -> !each.fatalCodes().isEmpty()).count();
        long judgeErrors = graded.stream()
                .filter(each -> each.verdict() != null && each.verdict().error() != null).count();

        StringBuilder sb = new StringBuilder();
        sb.append("# eval 채점 기록 — ").append(runDir.getFileName()).append("\n\n");
        sb.append("| 항목 | 값 |\n|---|---|\n");
        sb.append("| 채점 케이스 | ").append(graded.size()).append(" / ").append(LEDGER_SIZE).append(" |\n");
        // Judge를 돌리지 않았으면 "합격"이라고 쓸 수 없다 — 규칙층이 잡은 것이 없다는
        // 뜻일 뿐이고, 개수·인물 일치는 아직 아무도 보지 않았다
        sb.append(judging ? "| 합격 | " : "| 규칙층 통과(판정 미완) | ")
                .append(passed).append(" / ").append(graded.size()).append(" |\n");
        sb.append("| 치명(F1~F4) 케이스 | ").append(fatal).append(" |\n");
        sb.append("| Judge | ").append(judging ? "`" + Judge.MODEL + "`" : "생략(규칙층만)").append(" |\n");
        if (judgeErrors > 0) {
            sb.append("| **Judge 오류** | ").append(judgeErrors).append("건 — 그 케이스 판정은 무효 |\n");
        }
        sb.append("| 기대값 정본 | `docs/evals/eval-cases.md` |\n\n");

        sb.append("> **게이트 G1 기준**: 치명 0건 · 합격률 ≥ 90%(").append(PASS_THRESHOLD)
                .append('/').append(LEDGER_SIZE).append(") · **사람 승인**.\n");
        if (graded.size() < LEDGER_SIZE) {
            sb.append("> 이 회차는 전량이 아니다(").append(graded.size())
                    .append("케이스) — 게이트 판정의 입력이 될 수 없다.\n");
        }
        sb.append("> 이 문서는 판정의 <b>입력</b>이지 게이트 통과 선언이 아니다.\n\n");

        sb.append("## 케이스별\n\n");
        sb.append("| 케이스 | 판정 | 코드 | 근거 |\n|---|---|---|---|\n");
        for (Graded each : graded) {
            sb.append("| ").append(each.record().id())
                    .append(" | ").append(verdictLabel(each, judging))
                    .append(" | ").append(String.join(", ", each.allCodes()))
                    .append(" | ").append(reasonOf(each)).append(" |\n");
        }

        sb.append("\n## 규칙층 상세\n\n");
        for (Graded each : graded) {
            if (each.findings().isEmpty()) {
                continue;
            }
            sb.append("- **").append(each.record().id()).append("**\n");
            each.findings().forEach(finding ->
                    sb.append("  - ").append(finding).append('\n'));
        }

        Files.writeString(runDir.resolve("grade.md"), sb.toString(), StandardCharsets.UTF_8);
    }

    private static String verdictLabel(Graded graded, boolean judging) {
        if (!graded.pass()) {
            return "**불합격**";
        }

        return judging ? "합격" : "미완";
    }

    private static String reasonOf(Graded graded) {
        if (graded.verdict() != null && graded.verdict().error() != null) {
            return "Judge 오류 — " + oneLine(graded.verdict().error());
        }
        if (graded.ruleFailed()) {
            return oneLine(graded.findings().stream()
                    .filter(RuleGrader.Finding::countsAsFailure)
                    .map(RuleGrader.Finding::toString)
                    .reduce((a, b) -> a + " · " + b).orElse(""));
        }

        return graded.verdict() == null ? "(규칙층만 — 판정 미완)" : oneLine(graded.verdict().reason());
    }

    /** 표 한 칸에 들어가야 한다 — 줄바꿈과 파이프가 표를 깨뜨린다 */
    private static String oneLine(String text) {
        return text.replace("\n", " ").replace("|", "/");
    }
}
