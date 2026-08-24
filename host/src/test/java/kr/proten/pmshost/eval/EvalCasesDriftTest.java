package kr.proten.pmshost.eval;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실행용 케이스 판(`eval/cases.json`)이 원장(`docs/evals/eval-cases.md`)과 갈리지
 * 않음을 고정한다. 불일치 시 <b>문서가 이긴다</b> — json을 문서에 맞춰라
 * ({@link kr.proten.pmshost.chat.SystemPromptsDriftTest}와 같은 규율).
 *
 * <p>왜 필요한가: 케이스가 두 벌 존재하는 순간, 원장에서 화자를 바꾸거나 케이스를
 * 늘려도 러너는 옛 판을 계속 돈다. 그러면 "36케이스 전량 실행"이라는 게이트 문장이
 * 사실이 아니게 되는데, 그것을 눈으로는 발견할 수 없다.
 *
 * <p>고정하는 것은 <b>실행에 필요한 축</b>뿐이다 — id 집합·순서·분류별 개수·화자.
 * 기대값은 json에 아예 없으므로(설계) 대조할 것도 없다.
 */
class EvalCasesDriftTest {

    private static final Path LEDGER = Path.of("..", "docs", "evals", "eval-cases.md");

    /** `| A-01 | 박재완 (관리자) | …` — 첫 칸이 케이스 id인 행만 집는다 */
    private static final Pattern ROW = Pattern.compile("^\\|\\s*([A-H]-\\d{2})\\s*\\|([^|]*)\\|",
            Pattern.MULTILINE);

    /** `## A. 가동률 (8) — …` — 분류 문자와 그 분류가 선언한 케이스 수 */
    private static final Pattern SECTION = Pattern.compile("^## ([A-H])\\. [^(]*\\((\\d+)\\)",
            Pattern.MULTILINE);

    @Test
    @DisplayName("cases.json의 id 목록과 순서가 원장 표와 같다 (36케이스)")
    void idsMatchLedgerInOrder() throws Exception {
        List<String> ledgerIds = ledgerRows().keySet().stream().toList();
        List<String> runnerIds = EvalCases.load().stream().map(EvalCases.Case::id).toList();

        assertThat(runnerIds)
                .as("원장에 있는 케이스가 러너 판에 그대로, 같은 순서로 있어야 한다")
                .containsExactlyElementsOf(ledgerIds);
        assertThat(runnerIds).as("eval 셋은 36케이스다 (PRD-host §6-3)").hasSize(36);
    }

    @Test
    @DisplayName("분류별 개수가 원장 절 머리표가 선언한 수와 같다")
    void sectionCountsMatchHeadings() throws Exception {
        String doc = Files.readString(LEDGER, StandardCharsets.UTF_8);
        Map<String, Long> actual = new LinkedHashMap<>();
        for (EvalCases.Case c : EvalCases.load()) {
            actual.merge(c.id().substring(0, 1), 1L, Long::sum);
        }

        Matcher m = SECTION.matcher(doc);
        int sections = 0;
        while (m.find()) {
            sections++;
            String letter = m.group(1);
            assertThat(actual.get(letter))
                    .as("%s류 케이스 수 — 원장 머리표는 %s건이라고 말한다", letter, m.group(2))
                    .isEqualTo(Long.parseLong(m.group(2)));
        }
        assertThat(sections).as("분류는 A~H 8종이다").isEqualTo(8);
    }

    @Test
    @DisplayName("화자가 원장의 화자 칸과 같다 — 같은 입력도 화자가 다르면 기대값이 다르다")
    void speakersMatchLedger() throws Exception {
        Map<String, String> ledger = ledgerRows();

        for (EvalCases.Case c : EvalCases.load()) {
            String cell = ledger.get(c.id());
            if (c.anySpeaker()) {
                // 원장이 화자를 고정하지 않은 케이스(유지보수는 전사 공개라 화자 무관).
                // 러너는 그래도 한 명을 골라야 하므로 json에 실제 인물이 들어 있다.
                assertThat(cell).as("%s는 원장이 화자를 열어 둔 케이스여야 한다", c.id())
                        .contains("임의 화자");
            } else {
                assertThat(cell).as("%s의 화자", c.id()).contains(c.speaker().name());
            }
        }
    }

    /** 원장 표에서 (id → 화자 칸)을 순서대로 읽는다 */
    private static Map<String, String> ledgerRows() throws Exception {
        String doc = Files.readString(LEDGER, StandardCharsets.UTF_8);
        Map<String, String> rows = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();

        Matcher m = ROW.matcher(doc);
        while (m.find()) {
            if (rows.put(m.group(1), m.group(2).trim()) != null) {
                duplicates.add(m.group(1));
            }
        }

        assertThat(rows).as("원장에서 케이스 행을 읽어야 한다").isNotEmpty();
        assertThat(duplicates).as("원장에 같은 케이스 id가 두 번 나오면 안 된다").isEmpty();

        return rows;
    }
}
