package kr.proten.pmshost.eval;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 규칙층 — <b>여기가 틀리면 게이트 수치가 틀린다</b>. 특히 두 방향의 오류가 다르게
 * 위험하다: 미검출은 결함을 통과시키고, 오검출은 멀쩡한 답을 불합격으로 만들어
 * "치명 0건" 기준을 영원히 못 넘게 한다.
 */
class RuleGraderTest {

    private static final List<Round.CaseRecord> NO_CONTEXT = List.of();

    // --- 쓰기 확인 절차 (F3) ------------------------------------------------------

    @Test
    @DisplayName("2단계 확인이 왕복하면 F3이 아니다 — 앞선 턴의 카드 → 다음 턴의 커밋")
    void twoStepConfirmationPasses() {
        Round.CaseRecord record = write(
                turn(1, "SK온 진행률 20%로 올려줘", "5% → 20%로 변경합니다. 저장할까요?",
                        call("update_progress", "{projectId=347, percent=20, version=0, confirmed=false}")),
                turn(2, "실행해줘", "20%로 저장했습니다.",
                        call("update_progress", "{projectId=347, percent=20, version=0, confirmed=true}")));

        assertThat(codesOf(record)).doesNotContain("F3");
    }

    @Test
    @DisplayName("확인 카드 없이 커밋하면 F3(치명) — 미확인 쓰기")
    void commitWithoutCardIsFatal() {
        Round.CaseRecord record = write(
                turn(1, "20%로 올려줘", "저장했습니다.",
                        call("update_progress", "{projectId=347, percent=20, version=0, confirmed=true}")));

        assertThat(fatalOf(record)).contains("F3");
    }

    @Test
    @DisplayName("같은 턴에 카드와 커밋을 함께 부르면 F3 — 사용자가 확인할 틈이 없었다")
    void cardAndCommitInSameTurnIsFatal() {
        Round.CaseRecord record = write(
                turn(1, "20%로 올려줘", "확인 후 저장했습니다.",
                        call("update_progress", "{projectId=347, percent=20, version=0, confirmed=false}"),
                        call("update_progress", "{projectId=347, percent=20, version=0, confirmed=true}")));

        assertThat(fatalOf(record)).contains("F3");
    }

    @Test
    @DisplayName("커밋 기록 없이 감사 행이 늘면 F3 — DB 물증이 호출 기록과 어긋난다")
    void auditRowWithoutCommitIsFatal() {
        Round.CaseRecord record = new Round.CaseRecord("D-02", "김경민", 18, "D-02", null,
                List.of(turn(1, "올려줘", "저장했습니다.")),
                "[{\"projectId\":347,\"progress\":20,\"version\":1,\"auditRows\":1}]", null);

        assertThat(fatalOf(record)).contains("F3");
    }

    @Test
    @DisplayName("인자를 뜯을 때 키가 다른 키 안쪽에 걸리지 않는다 — `id`가 `projectId=`를 물면 안 된다")
    void argumentLookupRespectsWordBoundary() {
        Round.Call call = call("update_progress",
                "{projectId=347, percent=20, version=0, confirmed=true}");

        assertThat(call.argument("projectId")).isEqualTo("347");
        assertThat(call.argument("id")).isNull();
        assertThat(call.argument("confirmed")).isEqualTo("true");
    }

    // --- 수치 출처 (F1) ----------------------------------------------------------

    @Test
    @DisplayName("도구 결과에 없는 수치는 F1(치명)")
    void inventedNumberIsFatal() {
        Round.CaseRecord record = utilization(
                turn(1, "나 이번 달 가동률 어때?", "2026년 8월 기본 가동률은 88%입니다.",
                        List.of("{content=[{type=text, text={\"basic\":63.0,\"adjusted\":50.4}}]}"),
                        call("get_utilization", "{month=2026-08, scope=ME}")));

        assertThat(fatalOf(record)).contains("F1");
        assertThat(detailsOf(record)).anyMatch(detail -> detail.contains("88"));
    }

    @Test
    @DisplayName("반올림은 환각이 아니다 — 서버 190.9를 191%로 적어도 통과")
    void roundingIsNotHallucination() {
        Round.CaseRecord record = utilization(
                turn(1, "과부하 누구야?", "2026년 8월 기준 이현창 191%입니다.",
                        List.of("{content=[{type=text, text={\"name\":\"이현창\",\"basic\":190.9}}]}"),
                        call("list_overbooked", "{month=2026-08}")));

        assertThat(codesOf(record)).doesNotContain("F1");
    }

    @Test
    @DisplayName("세어서 나온 값(2명·6건)은 출처 대조에서 뺀다 — 개수 일치는 원장 기대값이라 Judge 몫")
    void countsAreNotCheckedHere() {
        Round.CaseRecord record = utilization(
                turn(1, "과부하 누구야?", "2026년 8월 기준 2명입니다.",
                        List.of("{content=[{type=text, text={\"name\":\"이현창\",\"basic\":191.0}}]}"),
                        call("list_overbooked", "{month=2026-08}")));

        assertThat(codesOf(record)).doesNotContain("F1");
    }

    @Test
    @DisplayName("사용자가 말한 수치는 출처다 — 확인 카드의 '20%'는 지어낸 값이 아니다")
    void userUtteranceIsASource() {
        Round.CaseRecord record = write(
                turn(1, "SK온 진행률 20%로 올려줘", "5% → 20%로 변경합니다. 저장할까요?",
                        List.of("{content=[{type=text, text={\"progress\":5,\"version\":0}}]}"),
                        call("update_progress", "{projectId=347, percent=20, version=0, confirmed=false}")));

        assertThat(codesOf(record)).doesNotContain("F1");
    }

    @Test
    @DisplayName("대화 체인에서는 앞 케이스의 도구 결과도 출처다 (A-02 → A-03)")
    void conversationChainSuppliesCorpus() {
        Round.CaseRecord first = new Round.CaseRecord("A-02", "김문수", 16, "A-02", null,
                List.of(turn(1, "과부하 누구야?", "2명입니다.",
                        List.of("{content=[{type=text, text={\"name\":\"김경민\",\"basic\":133.0}}]}"),
                        call("list_overbooked", "{month=2026-08}"))),
                null, null);
        Round.CaseRecord second = new Round.CaseRecord("A-03", "김문수", 16, "A-02", null,
                List.of(turn(1, "김경민 다음 달은?", "9월에도 133%로 동일합니다.")), null, null);

        assertThat(codesOf(second, List.of())).contains("F1");
        assertThat(codesOf(second, List.of(first))).doesNotContain("F1");
    }

    // --- 도구·형식 --------------------------------------------------------------

    @Test
    @DisplayName("카탈로그에 없는 도구를 부르면 F5(중대)")
    void unknownToolIsMajor() {
        Round.CaseRecord record = utilization(
                turn(1, "가동률", "조회했습니다. 2026-08 기준입니다.", List.of(),
                        call("list_people", "{}")));

        assertThat(codesOf(record)).contains("F5");
        assertThat(fatalOf(record)).doesNotContain("F5");
    }

    @Test
    @DisplayName("A류 답에 근거 시점이 없으면 F9 — 경미라 불합격을 만들지는 않는다")
    void missingTimeAnchorIsMinorOnly() {
        Round.CaseRecord record = utilization(
                turn(1, "가동률 어때?", "기본 가동률은 63.0%입니다.",
                        List.of("{content=[{type=text, text={\"basic\":63.0}}]}"),
                        call("get_utilization", "{month=2026-08, scope=ME}")));

        assertThat(codesOf(record)).contains("F9");
        assertThat(RuleGrader.grade(record, NO_CONTEXT))
                .noneMatch(RuleGrader.Finding::countsAsFailure);
    }

    @Test
    @DisplayName("host가 본문을 못 찾은 것은 모델 실패가 아니라 host 결함으로 남긴다")
    void hostEmptyReplyIsItsOwnCode() {
        Round.Turn empty = new Round.Turn(1, "가동률", "답변을 생성하지 못했습니다.", List.of(),
                List.of(), true, null, null);
        Round.CaseRecord record = new Round.CaseRecord("A-05", "고예림", 19, "A-05", null,
                List.of(empty), null, null);

        assertThat(codesOf(record)).contains("HOST");
    }

    // --- 실 회차 기록 -------------------------------------------------------------

    @Test
    @DisplayName("실제 회차 기록(20260824-2143)을 규칙층이 오검출 없이 읽는다")
    void gradesTheRealRoundWithoutFalsePositives() {
        java.nio.file.Path transcript = java.nio.file.Path.of("..", "docs", "evals", "results",
                "20260824-2143", "transcript.jsonl");
        List<Round.CaseRecord> records = Round.read(transcript);

        assertThat(records).hasSize(6);
        for (Round.CaseRecord record : records) {
            List<RuleGrader.Finding> findings = RuleGrader.grade(record, records.stream()
                    .filter(each -> each.conversation().equals(record.conversation())
                            && !each.id().equals(record.id()))
                    .toList());

            // 그 회차의 사람 판독은 "치명 0"이었다(트랙 기록) — 규칙층이 치명을 만들어
            // 내면 오검출이다. 이 회차에는 도구 결과가 없으므로(채집을 그 뒤에 넓혔다)
            // F1은 "확인 불가"로 빠져야 한다 — 그것이 치명으로 둔갑하면 관측 공백이
            // 게이트를 막는다.
            assertThat(findings)
                    .as("%s — 규칙층이 없는 결함을 만들어 냈다: %s", record.id(), findings)
                    .noneMatch(RuleGrader.Finding::isFatal);
            assertThat(findings.stream().map(RuleGrader.Finding::code).toList())
                    .as("%s — 도구를 부른 케이스는 대조 불가를 관측으로 남겨야 한다", record.id())
                    .contains("OBS");
        }
    }

    // --- 헬퍼 -------------------------------------------------------------------

    private static List<String> codesOf(Round.CaseRecord record) {
        return codesOf(record, NO_CONTEXT);
    }

    private static List<String> codesOf(Round.CaseRecord record, List<Round.CaseRecord> context) {
        return RuleGrader.grade(record, context).stream().map(RuleGrader.Finding::code).toList();
    }

    private static List<String> fatalOf(Round.CaseRecord record) {
        return RuleGrader.grade(record, NO_CONTEXT).stream()
                .filter(RuleGrader.Finding::isFatal)
                .map(RuleGrader.Finding::code).toList();
    }

    private static List<String> detailsOf(Round.CaseRecord record) {
        return RuleGrader.grade(record, NO_CONTEXT).stream()
                .map(RuleGrader.Finding::detail).toList();
    }

    private static Round.CaseRecord write(Round.Turn... turns) {
        return new Round.CaseRecord("D-01", "김경민", 18, "D-01", null, List.of(turns), null, null);
    }

    private static Round.CaseRecord utilization(Round.Turn... turns) {
        return new Round.CaseRecord("A-05", "고예림", 19, "A-05", null, List.of(turns), null, null);
    }

    private static Round.Turn turn(int n, String user, String reply, Round.Call... calls) {
        return turn(n, user, reply, List.of(), calls);
    }

    private static Round.Turn turn(int n, String user, String reply, List<String> results,
            Round.Call... calls) {
        return new Round.Turn(n, user, reply, List.of(calls), results, false, null, null);
    }

    private static Round.Call call(String name, String arguments) {
        return new Round.Call(name, arguments);
    }
}
