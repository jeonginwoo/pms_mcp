package kr.proten.pmshost.eval;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * 회차 기록(`transcript.jsonl`)을 읽는다 — 채점층의 <b>유일한 입력</b>이다.
 *
 * <p>실행과 채점을 가른 이유가 여기 있다: 입력이 파일이므로 실 LLM 회차를 다시
 * 태우지 않고 재채점할 수 있다. 채점 규칙을 고칠 때마다 회차를 버리면 회차 하나가
 * 곧 비용이라 규칙을 못 고치게 된다.
 *
 * <p>기록에 없는 필드는 <b>비운다</b>(예외를 던지지 않는다) — 관측 지점이 늘어난
 * 뒤에도 옛 회차를 읽을 수 있어야 회귀 비교가 성립한다. 실제로 2026-08-24 첫 회차에는
 * `toolResults`가 없다(그 회차 뒤에 채집을 넓혔다).
 */
final class Round {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Round() {
    }

    /**
     * @param name      도구명 — 파싱 실패 시 null
     * @param arguments 인자 원문 — F5·F3 판정의 근거
     */
    record Call(String name, String arguments) {

        /**
         * `{projectId=347, percent=20, version=0, confirmed=false}` 안의 한 값.
         *
         * <p>키 앞이 낱말 문자가 아닌 자리만 찾는다 — 그냥 찾으면 `id`가 `projectId=`
         * 안쪽에 걸려 엉뚱한 값을 준다.
         */
        String argument(String key) {
            if (arguments == null) {
                return null;
            }
            int at = -1;
            for (int i = arguments.indexOf(key + "="); i >= 0;
                    i = arguments.indexOf(key + "=", i + 1)) {
                if (i == 0 || !Character.isLetterOrDigit(arguments.charAt(i - 1))) {
                    at = i;
                    break;
                }
            }
            if (at < 0) {
                return null;
            }
            int from = at + key.length() + 1;
            int end = from;
            while (end < arguments.length() && ",}".indexOf(arguments.charAt(end)) < 0) {
                end++;
            }

            return arguments.substring(from, end).trim();
        }
    }

    /**
     * @param n         턴 번호
     * @param user      사용자 발화 — 주입 턴이면 null
     * @param reply     모델 응답 — 실행 실패면 null
     * @param calls     그 턴의 도구 호출
     * @param results   그 턴의 도구 결과 본문 (F1 대조 말뭉치)
     * @param hostEmpty host가 본문을 못 찾은 사건 — 모델의 무응답(F6)과 다르다
     * @param error     턴 실행 오류
     * @param injected  주입 기록(턴 경계) — 채점 대상이 아니라 맥락이다
     */
    record Turn(int n, String user, String reply, List<Call> calls, List<String> results,
            boolean hostEmpty, String error, String injected) {

        boolean isInjection() {
            return injected != null;
        }
    }

    /**
     * @param id           케이스 id
     * @param speaker      화자 이름
     * @param personId     화자 id
     * @param conversation 대화 체인의 뿌리 케이스 id
     * @param inject       주입 종류 (contaminate·concurrentWrite·toolFault) — 없으면 null
     * @param turns        턴 목록 (주입 턴 포함)
     * @param dbAfter      쓰기 케이스의 DB 결과 원문 — 없으면 null
     * @param error        케이스 실행 오류
     */
    record CaseRecord(String id, String speaker, int personId, String conversation, String inject,
            List<Turn> turns, String dbAfter, String error) {

        /** 주입 턴을 뺀 실제 대화 턴 */
        List<Turn> spokenTurns() {
            return turns.stream().filter(t -> !t.isInjection()).toList();
        }
    }

    static List<CaseRecord> read(Path transcript) {
        List<CaseRecord> records = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(transcript, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                records.add(caseOf(JSON.readTree(line)));
            }
        } catch (Exception e) {
            throw new IllegalStateException("회차 기록을 읽지 못했다: " + transcript.toAbsolutePath(), e);
        }
        if (records.isEmpty()) {
            throw new IllegalStateException("회차 기록이 비어 있다: " + transcript.toAbsolutePath());
        }

        return List.copyOf(records);
    }

    /** 회차 디렉터리를 고른다 — 지정이 없으면 이름순 가장 최근(runId가 `yyyyMMdd-HHmm`) */
    static Path resolveRun(Path resultsDir, String runId) {
        if (runId != null && !runId.isBlank()) {
            Path picked = resultsDir.resolve(runId.trim());
            if (!Files.isDirectory(picked)) {
                throw new IllegalStateException("그런 회차가 없다: " + picked.toAbsolutePath());
            }

            return picked;
        }

        try (var dirs = Files.list(resultsDir)) {
            return dirs.filter(Files::isDirectory)
                    .max(Comparator.comparing(p -> p.getFileName().toString()))
                    .orElseThrow(() -> new IllegalStateException(
                            "회차 기록이 하나도 없다: " + resultsDir.toAbsolutePath()));
        } catch (Exception e) {
            throw new IllegalStateException("회차 목록을 읽지 못했다: " + resultsDir.toAbsolutePath(), e);
        }
    }

    private static CaseRecord caseOf(JsonNode node) {
        List<Turn> turns = new ArrayList<>();
        for (JsonNode turn : node.path("turns")) {
            turns.add(turnOf(turn));
        }

        return new CaseRecord(
                node.path("id").asText(),
                node.path("speaker").path("name").asText(""),
                node.path("speaker").path("personId").asInt(0),
                node.path("conversation").asText(""),
                text(node, "inject"),
                List.copyOf(turns),
                node.hasNonNull("dbAfter") ? node.get("dbAfter").toString() : null,
                text(node, "error"));
    }

    private static Turn turnOf(JsonNode node) {
        List<Call> calls = new ArrayList<>();
        for (JsonNode call : node.path("toolCalls")) {
            calls.add(new Call(text(call, "name"), text(call, "arguments")));
        }
        List<String> results = new ArrayList<>();
        for (JsonNode result : node.path("toolResults")) {
            // 파싱이 깨진 결과는 원문이라도 넘긴다 — 말뭉치가 조용히 비면 F1이 오검출된다
            String payload = text(result, "payload");
            results.add(payload != null ? payload : text(result, "raw"));
        }

        return new Turn(
                node.path("n").asInt(0),
                text(node, "user"),
                text(node, "reply"),
                List.copyOf(calls),
                results.stream().filter(java.util.Objects::nonNull).toList(),
                node.has("hostEmptyReply"),
                text(node, "error"),
                text(node, "injected"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);

        return value == null || value.isNull() ? null : value.asText();
    }
}
