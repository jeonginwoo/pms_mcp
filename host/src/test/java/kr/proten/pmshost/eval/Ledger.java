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

/**
 * 원장(`docs/evals/eval-cases.md`) 표를 그대로 읽는다 — <b>채점 기준의 정본은 이
 * 문서 한 벌</b>이다.
 *
 * <p>이 클래스가 존재하는 이유가 설계의 핵심이다. 케이스별 기대값("6건", "2명 —
 * 이현창 191·김경민 133")을 기계 판으로 옮기면 원장이 둘이 되고, 채점이 어느 쪽을
 * 보는지가 사람마다 갈린다(2026-08-24 결정 — 러너의 `cases.json`이 기대값을 담지
 * 않는 것과 같은 이유). 그래서 <b>기대값을 복제하는 대신 원장 행을 그대로 루브릭으로
 * 넘긴다</b> — 규칙층은 기대값이 필요 없는 것만 보고(수치 출처·확인 절차·길이),
 * 프로즈로 적힌 기준은 LLM-Judge가 이 행을 읽고 판정한다.
 *
 * <p>절 머리표({@code ## A. 가동률 (8) — …})도 함께 나른다: 앵커 수치와 모집단
 * 규칙이 거기 적혀 있어(예: "2026-08 과부하 = 이현창 191·김경민 133") 행만으로는
 * 판정이 서지 않는 케이스가 있다.
 */
final class Ledger {

    static final Path PATH = Path.of("..", "docs", "evals", "eval-cases.md");

    /** `## A. 가동률 (8) — …` — 분류 문자와 그 절 머리표 전문 */
    private static final Pattern SECTION = Pattern.compile("^## ([A-H])\\. (.*)$");
    /** `| A-01 | 박재완 (관리자) | …` — 첫 칸이 케이스 id인 행 */
    private static final Pattern ROW = Pattern.compile("^\\|\\s*([A-H]-\\d{2})\\s*\\|.*");

    /** 표의 칸 수 — ID·화자·입력·기대 도구 흐름·합격 기준·평가층·원천 */
    private static final int COLUMNS = 7;

    private Ledger() {
    }

    /**
     * @param id           케이스 id
     * @param speaker      화자 칸 원문 (`박재완 (관리자)` / `(임의 화자)`)
     * @param input        입력 칸 원문 — 후속 발화가 `→ (후속)`으로 이어진다
     * @param expectedFlow 기대 도구 흐름 칸 원문 (기대 수치가 여기 실린다)
     * @param criteria     합격 기준 칸 원문 — 판정의 본문
     * @param layer        평가층 칸 (`규칙` / `규칙+LLM` / `LLM`)
     * @param source       원천 칸 (유저 시나리오·구 케이스 번호)
     * @param section      이 케이스가 속한 절 머리표 전문 — 앵커·모집단 규칙
     */
    record Rubric(String id, String speaker, String input, String expectedFlow, String criteria,
            String layer, String source, String section) {

        /** A~H 분류 문자 */
        String category() {
            return id.substring(0, 1);
        }

        /** Judge에게 넘기는 판정 기준 한 벌 — 원장 문장을 <b>손대지 않고</b> 옮긴다 */
        String rubricText() {
            return """
                    [케이스] %s
                    [화자] %s
                    [입력] %s
                    [기대 도구 흐름] %s
                    [합격 기준] %s
                    [분류 머리표(앵커·모집단 규칙)] %s""".formatted(
                    id, speaker, input, expectedFlow, criteria, section);
        }
    }

    static List<Rubric> load() {
        return load(PATH);
    }

    static List<Rubric> load(Path path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("원장을 읽지 못했다: " + path.toAbsolutePath(), e);
        }

        List<Rubric> rubrics = new ArrayList<>();
        String section = "";
        for (String line : lines) {
            Matcher heading = SECTION.matcher(line);
            if (heading.matches()) {
                section = heading.group(1) + ". " + heading.group(2).trim();
                continue;
            }
            if (!ROW.matcher(line).matches()) {
                continue;
            }
            String[] cells = cellsOf(line);
            rubrics.add(new Rubric(cells[0], cells[1], cells[2], cells[3], cells[4], cells[5],
                    cells[6], section));
        }

        if (rubrics.isEmpty()) {
            throw new IllegalStateException("원장에서 케이스 행을 하나도 읽지 못했다: " + path);
        }

        return List.copyOf(rubrics);
    }

    /** id → 루브릭 (원장 순서 유지) */
    static Map<String, Rubric> byId() {
        Map<String, Rubric> byId = new LinkedHashMap<>();
        for (Rubric rubric : load()) {
            if (byId.put(rubric.id(), rubric) != null) {
                throw new IllegalStateException("원장에 같은 케이스 id가 두 번 있다: " + rubric.id());
            }
        }

        return byId;
    }

    /**
     * 한 행을 칸으로 가른다. 칸 수가 다르면 <b>던진다</b> — 조용히 채우면 합격 기준이
     * 빈 채로 Judge에 가고, 그러면 "기준 없이 통과"가 된다.
     */
    private static String[] cellsOf(String row) {
        String trimmed = row.strip();
        // 양끝 파이프를 떼고 가른다 — split이 앞뒤에 빈 칸을 만드는 것을 피한다
        String inner = trimmed.substring(1, trimmed.endsWith("|") ? trimmed.length() - 1 : trimmed.length());
        String[] cells = inner.split("\\|", -1);
        if (cells.length != COLUMNS) {
            throw new IllegalStateException(
                    "원장 표의 칸 수가 %d이 아니다(%d): %s".formatted(COLUMNS, cells.length, trimmed));
        }
        for (int i = 0; i < cells.length; i++) {
            cells[i] = cells[i].trim();
        }

        return cells;
    }
}
