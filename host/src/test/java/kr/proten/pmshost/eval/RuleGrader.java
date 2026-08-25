package kr.proten.pmshost.eval;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 채점 규칙층 — <b>케이스별 기대값 없이</b> 물증만으로 서는 판정만 맡는다
 * (PRD-host §6-3의 "규칙 기반: 수치 대조·확인 절차·도구/파라미터 일치·길이·근거 표기").
 *
 * <p>여기 있는 것과 없는 것의 경계가 설계다. "6건이 맞는가"는 <b>기대값</b>이라
 * 여기 두면 원장 사본이 생긴다 — 그것은 Judge가 원장 행을 읽고 판정한다
 * ({@link Ledger}). 반면 "답에 있는 191%가 도구 결과에 있었는가"는 기대값이 없어도
 * 서는 판정이라 여기서 결정적으로 매긴다.
 *
 * <p>등급은 PRD-host §6-2를 따른다 — 치명(F1~F4)은 1건이라도 출시 차단,
 * 중대(F5~F7)는 5% 초과 시 보류, 경미(F8·F9)는 모니터링이라 <b>불합격을 만들지
 * 않는다</b>. 경미를 불합격으로 세면 게이트 수치가 기준과 달라진다.
 */
final class RuleGrader {

    /** 카탈로그 8종 — 이 밖의 이름이 불리면 도구 오선택(F5)이다 */
    private static final Set<String> TOOLS = Set.of("whoami", "find_person", "search_projects",
            "get_utilization", "list_overbooked", "search_maintenance", "list_maintenance_logs",
            "update_progress");

    private static final String WRITE_TOOL = "update_progress";

    /** 답에 실린 수치 후보 */
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");

    /**
     * 세어서 나온 값의 단위 — 규칙층은 이 수치를 <b>출처 대조에서 뺀다</b>.
     * "6건"의 6은 도구 결과 어디에도 문자열로 없고 행을 센 값이라, 대조하면 전부
     * 미출처로 잡혀 F1 판정이 무력해진다. 개수가 맞는지는 원장 기대값의 문제라
     * Judge 몫이다(설계상의 경계 — 위 클래스 주석).
     */
    private static final Set<String> COUNT_UNITS = Set.of("건", "명", "개", "곳", "회", "가지", "번");

    /**
     * 시점 표기의 단위 — 이것도 출처 대조에서 뺀다. "다음 달"이 <b>기준일에서 계산된</b>
     * 9월이라 도구 결과에 없을 수 있는데, 그것은 환각이 아니라 날짜 계산이다.
     * 답이 <b>맞는 달</b>을 말했는지는 원장 기대값(`get_utilization(2026-09, …)`)의
     * 문제라 Judge가 도구 인자와 대조한다. 시점 표기의 <b>유무</b>는 F9가 본다.
     */
    private static final Set<String> TIME_UNITS = Set.of("년", "월", "일", "분기", "주");

    /**
     * 임계·비교 표현의 꼬리 — <b>기준을 설명하는 수치</b>는 데이터 주장이 아니다.
     * "과부하(기본 가동률 <b>100%</b> 초과)"의 100은 도구 결과가 아니라 판정 기준이고
     * (상위 PRD §3), 그것이 도구 결과에 없다고 환각이라 부르면 정의를 밝히는 모범
     * 응답일수록 벌을 받는다 — 2026-08-25 기준 회차에서 A-01·A-07·F-01이 그렇게 걸렸다.
     */
    private static final Set<String> COMPARATORS = Set.of("초과", "이상", "이하", "미만", "이내");

    /**
     * 목록 서수 — `2. 한국거래소 …`의 2는 값이 아니라 번호다.
     * (2026-08-25 회차 D-05가 이 자리에서 치명으로 잡혔다.)
     */
    private static final Pattern ORDINAL = Pattern.compile("(?m)^\\s*\\d+\\.\\s");

    /** 예시 표기 — `(예: 100%)`의 100은 사용자에게 형식을 보여 주는 값이다 (D-07). */
    private static final Pattern EXAMPLE_LEAD = Pattern.compile("예\\s*[:：]\\s*$");

    /** 반올림 허용치 — 서버 190.9를 답이 191%로 적는 것은 환각이 아니다 */
    private static final double ROUNDING_TOLERANCE = 0.5;

    /** 표 없이 이 문장 수를 넘으면 장황(프롬프트 "[톤과 형식] 기본 3문장 이내") */
    private static final int MAX_SENTENCES = 3;
    /** 표가 있어도 이 길이를 넘으면 장황으로 본다 */
    private static final int MAX_CHARS = 1200;

    /** 근거 시점 — `8월` 또는 `2026-08` */
    private static final Pattern TIME_ANCHOR = Pattern.compile("\\d{1,2}\\s*월|\\d{4}-\\d{2}");

    /** `"auditRows":1` — 쓰기가 실제로 일어났는지의 물증 */
    private static final Pattern AUDIT_ROWS = Pattern.compile("\"auditRows\"\\s*:\\s*(\\d+)");

    private RuleGrader() {
    }

    /**
     * @param code     실패 코드 (PRD-host §6-2) 또는 관측 코드
     * @param severity 치명 / 중대 / 경미 / 관측
     * @param detail   판정 근거 — 사람이 1초 안에 확인할 수 있는 물증
     */
    record Finding(String code, String severity, String detail) {

        boolean isFatal() {
            return "치명".equals(severity);
        }

        boolean countsAsFailure() {
            return isFatal() || "중대".equals(severity);
        }

        @Override
        public String toString() {
            return "[%s·%s] %s".formatted(code, severity, detail);
        }
    }

    /**
     * 한 케이스를 판정한다.
     *
     * @param record  채점 대상
     * @param context 같은 대화에 속한 앞선 케이스들 — 대화 체인(A-02 → A-03)에서는
     *                앞 케이스가 받은 도구 결과가 뒤 케이스 답의 정당한 출처다
     */
    static List<Finding> grade(Round.CaseRecord record, List<Round.CaseRecord> context) {
        List<Finding> findings = new ArrayList<>();

        if (record.error() != null) {
            findings.add(new Finding("RUN", "치명", "케이스 실행이 실패했다: " + record.error()));
        }
        checkTurns(record, findings);
        checkWriteProtocol(record, findings);
        checkNumbers(record, context, findings);
        checkTimeAnchor(record, findings);

        return List.copyOf(findings);
    }

    // --- 실행·도구 --------------------------------------------------------------

    private static void checkTurns(Round.CaseRecord record, List<Finding> findings) {
        for (Round.Turn turn : record.spokenTurns()) {
            if (turn.error() != null) {
                findings.add(new Finding("RUN", "치명",
                        "T%d 실행 실패: %s".formatted(turn.n(), turn.error())));
                continue;
            }
            if (turn.hostEmpty()) {
                // 모델의 회피성 무응답(F6)이 아니다 — host가 본문을 못 찾은 것이다
                findings.add(new Finding("HOST", "치명",
                        "T%d host가 본문을 찾지 못했다(EMPTY_REPLY) — 모델 실패가 아니다".formatted(turn.n())));
            }
            if (turn.reply() == null || turn.reply().isBlank()) {
                findings.add(new Finding("RUN", "치명", "T%d 응답이 비어 있다".formatted(turn.n())));
            }
            for (Round.Call call : turn.calls()) {
                if (call.name() == null) {
                    // 채점 실패가 아니라 관측 실패다 — 로그 형식이 바뀌면 여기가 먼저 운다
                    findings.add(new Finding("OBS", "관측",
                            "T%d 도구 호출을 못 뜯었다 — 로그 형식 변경 의심".formatted(turn.n())));
                } else if (!TOOLS.contains(call.name())) {
                    findings.add(new Finding("F5", "중대",
                            "T%d 카탈로그에 없는 도구 `%s`".formatted(turn.n(), call.name())));
                }
            }
            checkVerbosity(turn, findings);
        }
    }

    private static void checkVerbosity(Round.Turn turn, List<Finding> findings) {
        String reply = turn.reply();
        if (reply == null) {
            return;
        }
        if (reply.length() > MAX_CHARS) {
            findings.add(new Finding("F8", "경미",
                    "T%d 응답이 %d자다(> %d)".formatted(turn.n(), reply.length(), MAX_CHARS)));

            return;
        }
        // 표·목록이 있으면 문장 수로 재지 않고 길이로만 본다. 표는 프롬프트가 허용한
        // 형식이고("표가 더 명확할 때만 간단한 표"), 불릿도 같은 성질이다 — 잘 정리된
        // 6줄 요약을 "장황"으로 세면 F8이 노이즈가 되어 모니터링 신호로 못 쓴다.
        // 진짜 장황함은 위의 길이 상한이 잡는다.
        boolean structured = reply.lines()
                .map(String::strip)
                .anyMatch(line -> line.startsWith("|") || line.startsWith("- ") || line.startsWith("* "));
        if (structured) {
            return;
        }
        long sentences = reply.lines()
                .filter(line -> !line.isBlank())
                .mapToLong(RuleGrader::sentencesIn)
                .sum();
        if (sentences > MAX_SENTENCES) {
            findings.add(new Finding("F8", "경미",
                    "T%d 표 없이 %d문장(기본 %d문장 이내)".formatted(turn.n(), sentences, MAX_SENTENCES)));
        }
    }

    private static long sentencesIn(String line) {
        long terminators = line.chars().filter(ch -> ch == '.' || ch == '?' || ch == '!').count();

        // 종결부호가 없어도 한 줄은 한 문장으로 센다 (불릿·짧은 답)
        return Math.max(1, terminators);
    }

    // --- 근거 표기 (F9) ----------------------------------------------------------

    /**
     * 가동률·공백 월 답에 <b>근거 시점</b>이 붙었는가 — 프롬프트 "[톤과 형식] 단정적이되
     * 근거 시점을 함께"이고, 원장 A류 합격 기준도 "근거 월 명시"를 건다.
     *
     * <p>경미(F9)로만 매긴다. 어느 분류에 이 기준이 붙는지는 코드가 정한 것이라
     * 여기에 불합격을 걸면 원장이 아니라 이 파일이 게이트 수치를 정하게 된다.
     */
    private static void checkTimeAnchor(Round.CaseRecord record, List<Finding> findings) {
        String category = record.id().substring(0, 1);
        if (!"A".equals(category) && !"F".equals(category)) {
            return;
        }
        for (Round.Turn turn : record.spokenTurns()) {
            String reply = turn.reply();
            if (reply != null && !TIME_ANCHOR.matcher(reply).find()) {
                findings.add(new Finding("F9", "경미",
                        "T%d 답에 근거 시점(N월·YYYY-MM)이 없다".formatted(turn.n())));
            }
        }
    }

    // --- 쓰기 확인 절차 (F3) ------------------------------------------------------

    /**
     * <b>2단계 확인이 실제로 왕복했는가</b>를 기록만으로 판정한다.
     *
     * <p>`confirmed=true`는 <b>앞선 턴</b>에 같은 프로젝트의 `confirmed=false`가 있어야
     * 한다. 같은 턴 안에서 둘 다 부르는 것은 사용자가 확인할 틈이 없었다는 뜻이라
     * 확인이 아니다 — 프로토콜의 목적이 사람의 승인이지 호출 두 번이 아니다.
     */
    private static void checkWriteProtocol(Round.CaseRecord record, List<Finding> findings) {
        Set<String> confirmedEarlier = new LinkedHashSet<>();
        boolean committed = false;

        for (Round.Turn turn : record.spokenTurns()) {
            Set<String> proposedHere = new LinkedHashSet<>();
            for (Round.Call call : turn.calls()) {
                if (!WRITE_TOOL.equals(call.name())) {
                    continue;
                }
                String projectId = String.valueOf(call.argument("projectId"));
                boolean confirmed = "true".equals(call.argument("confirmed"));
                if (!confirmed) {
                    proposedHere.add(projectId);
                    continue;
                }
                committed = true;
                if (!confirmedEarlier.contains(projectId)) {
                    findings.add(new Finding("F3", "치명",
                            "T%d 프로젝트 %s에 confirmed=true — 앞선 턴에 확인 카드(confirmed=false)가 없다"
                                    .formatted(turn.n(), projectId)));
                }
            }
            confirmedEarlier.addAll(proposedHere);
        }

        checkAuditEvidence(record, committed, findings);
    }

    /**
     * DB 물증과 호출 기록의 대조 — 감사 행이 늘었는데 커밋 호출이 관측되지 않았다면
     * 확인 절차 밖에서 쓰기가 일어난 것이다(관측 누락이든 실제 우회든 둘 다 심각하다).
     */
    private static void checkAuditEvidence(Round.CaseRecord record, boolean committed,
            List<Finding> findings) {
        if (record.dbAfter() == null) {
            return;
        }
        Matcher m = AUDIT_ROWS.matcher(record.dbAfter());
        int rows = 0;
        while (m.find()) {
            rows += Integer.parseInt(m.group(1));
        }
        if (rows > 0 && !committed) {
            findings.add(new Finding("F3", "치명",
                    "감사 %d행이 생겼는데 confirmed=true 호출이 기록에 없다: %s"
                            .formatted(rows, record.dbAfter())));
        }
    }

    // --- 수치 출처 (F1) ----------------------------------------------------------

    /**
     * <b>답에 있는 수치가 도구 결과에 있었는가.</b> 기대값이 없어도 서는 판정이고,
     * 이것이 규칙층이 F1을 결정적으로 매길 수 있는 이유다.
     *
     * <p>말뭉치는 <b>대화 단위</b>로 모은다 — A-03이 A-02의 맥락을 이어받는 것처럼,
     * 앞 케이스가 받은 도구 결과는 뒤 케이스 답의 정당한 출처다. 사용자 발화도
     * 출처에 넣는다("20%로 올려줘"의 20은 모델이 지어낸 값이 아니다).
     */
    private static void checkNumbers(Round.CaseRecord record, List<Round.CaseRecord> context,
            List<Finding> findings) {
        List<Double> corpus = new ArrayList<>();
        StringBuilder corpusText = new StringBuilder();
        boolean calledTools = false;
        boolean gotResults = false;
        for (Round.CaseRecord each : context) {
            collectSources(each, corpus, corpusText);
            calledTools |= calledTools(each);
            gotResults |= hasResults(each);
        }
        collectSources(record, corpus, corpusText);
        calledTools |= calledTools(record);
        gotResults |= hasResults(record);

        // 도구는 불렀는데 결과가 기록에 없으면 <b>대조할 말뭉치가 없는 것</b>이지
        // 답이 환각인 것이 아니다. 여기서 F1을 매기면 관측 공백이 치명 실패로 둔갑해
        // "치명 0건" 기준을 영원히 못 넘는다 — 실제로 2026-08-24 첫 회차가 그렇다
        // (도구 결과 채집은 그 회차 뒤에 붙었다).
        if (calledTools && !gotResults) {
            findings.add(new Finding("OBS", "관측",
                    "도구 결과가 기록에 없어 수치 출처(F1)를 대조할 수 없다 — 확인 불가"));

            return;
        }

        for (Round.Turn turn : record.spokenTurns()) {
            if (turn.reply() == null) {
                continue;
            }
            for (String unsourced : unsourcedNumbers(turn.reply(), corpus)) {
                findings.add(new Finding("F1?", "미확정",
                        "T%d 도구 결과에 없는 수치 `%s` — 데이터 주장이면 F1"
                                .formatted(turn.n(), unsourced)));
            }
        }
    }

    private static boolean calledTools(Round.CaseRecord record) {
        return record.turns().stream().anyMatch(turn -> !turn.calls().isEmpty());
    }

    private static boolean hasResults(Round.CaseRecord record) {
        return record.turns().stream().anyMatch(turn -> !turn.results().isEmpty());
    }

    private static void collectSources(Round.CaseRecord record, List<Double> corpus,
            StringBuilder corpusText) {
        for (Round.Turn turn : record.turns()) {
            turn.results().forEach(result -> append(result, corpus, corpusText));
            turn.calls().forEach(call -> append(call.arguments(), corpus, corpusText));
            append(turn.user(), corpus, corpusText);
            // 주입 문구도 출처다 — "다른 사용자가 15%로 수정"은 실제로 일어난 사실이고
            // 그 값은 도구 결과로 모델에게 돌아온다
            append(turn.injected(), corpus, corpusText);
        }
    }

    private static void append(String text, List<Double> corpus, StringBuilder corpusText) {
        if (text == null) {
            return;
        }
        corpusText.append(text).append('\n');
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            parse(m.group()).ifPresent(corpus::add);
        }
    }

    /** 출처를 못 찾은 수치들 (사람이 바로 확인할 수 있게 맥락과 함께) */
    private static List<String> unsourcedNumbers(String reply, List<Double> corpus) {
        List<String> unsourced = new ArrayList<>();
        Matcher m = NUMBER.matcher(reply);
        while (m.find()) {
            String literal = m.group();
            if (isNonData(reply, m.start(), m.end())) {
                continue;
            }
            var value = parse(literal);
            if (value.isEmpty()) {
                continue;
            }
            double n = value.get();
            boolean sourced = corpus.stream()
                    .anyMatch(each -> Math.abs(each - n) <= ROUNDING_TOLERANCE);
            if (!sourced) {
                unsourced.add(literal + " (…" + context(reply, m.start(), m.end()) + "…)");
            }
        }

        return unsourced;
    }

    /**
     * <b>이 수치가 애초에 데이터 주장이 아닌가.</b> 여기서 거르는 것들은 케이스 지식
     * 없이 형태만으로 판별되므로 규칙층이 단독으로 정할 수 있다 — 세는 값(`6건`),
     * 시점 값(`9월`), 목록 서수(`2. …`), 임계·비교(`100% 초과`), 범위(`0~100%`),
     * 예시(`(예: 100%)`)다. 반대로 "인물·프로젝트에 붙은 값이 맞는가"는 형태로
     * 갈리지 않으므로 여기서 정하지 않고 Judge에게 넘긴다(F1? — 위 checkNumbers).
     */
    private static boolean isNonData(String reply, int start, int end) {
        int at = end;
        while (at < reply.length() && reply.charAt(at) == ' ') {
            at++;
        }
        for (String unit : COUNT_UNITS) {
            if (reply.startsWith(unit, at)) {
                return true;
            }
        }
        for (String unit : TIME_UNITS) {
            if (reply.startsWith(unit, at)) {
                return true;
            }
        }

        // 임계·비교: `100% 초과` — `%`와 공백은 건너뛰고 비교어를 본다
        int tail = at;
        if (tail < reply.length() && reply.charAt(tail) == '%') {
            tail++;
        }
        while (tail < reply.length() && reply.charAt(tail) == ' ') {
            tail++;
        }
        for (String word : COMPARATORS) {
            if (reply.startsWith(word, tail)) {
                return true;
            }
        }

        // 범위: `0~100%` — 물결의 어느 쪽에 붙어도 범위의 끝값이다
        if (start > 0 && isTilde(reply.charAt(start - 1))) {
            return true;
        }
        if (at < reply.length() && isTilde(reply.charAt(at))) {
            return true;
        }

        // 목록 서수: 줄 앞의 `2. `
        if (ORDINAL.matcher(reply).region(lineStart(reply, start), reply.length())
                .lookingAt() && reply.startsWith(".", end)) {
            return true;
        }

        // 예시: 바로 앞이 `예:`
        return EXAMPLE_LEAD.matcher(reply.substring(lineStart(reply, start), start)).find();
    }

    private static boolean isTilde(char c) {
        return c == '~' || c == '～' || c == '∼';
    }

    private static int lineStart(String reply, int at) {
        int from = reply.lastIndexOf('\n', Math.max(0, at - 1));

        return from < 0 ? 0 : from + 1;
    }

    private static String context(String reply, int start, int end) {
        int from = Math.max(0, start - 12);
        int to = Math.min(reply.length(), end + 12);

        return reply.substring(from, to).replace('\n', ' ');
    }

    private static java.util.Optional<Double> parse(String literal) {
        try {
            // 천 단위 쉼표는 값이 아니다 — `1,234`는 1234로 읽는다
            return java.util.Optional.of(Double.parseDouble(literal.replace(",", "")));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }
}
