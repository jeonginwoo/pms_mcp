package kr.proten.pmshost.eval;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 원장 파서 — <b>루브릭이 비면 "기준 없이 통과"가 된다</b>. Judge는 받은 문장만
 * 보므로 합격 기준 칸을 못 읽어도 판정은 그럴듯하게 돌아오고, 그 사실이 겉으로
 * 드러나지 않는다. 그래서 칸이 비었는지를 여기서 단정한다.
 */
class LedgerTest {

    @Test
    @DisplayName("36케이스를 모든 칸과 함께 읽는다 — 빈 칸이 있으면 채점 기준이 빈 것이다")
    void readsEveryCaseWithFullRubric() {
        List<Ledger.Rubric> rubrics = Ledger.load();

        assertThat(rubrics).as("eval 셋은 36케이스다 (PRD-host §6-3)").hasSize(36);
        for (Ledger.Rubric rubric : rubrics) {
            assertThat(rubric.input()).as("%s 입력", rubric.id()).isNotBlank();
            assertThat(rubric.expectedFlow()).as("%s 기대 도구 흐름", rubric.id()).isNotBlank();
            assertThat(rubric.criteria()).as("%s 합격 기준", rubric.id()).isNotBlank();
            assertThat(rubric.section()).as("%s 분류 머리표", rubric.id())
                    .startsWith(rubric.category() + ".");
        }
    }

    @Test
    @DisplayName("Judge에 넘기는 루브릭에 원장의 기대 수치가 그대로 실린다")
    void rubricCarriesLedgerNumbers() {
        Ledger.Rubric a02 = Ledger.byId().get("A-02");

        // 이 수치들은 코드가 아니라 원장에서 온다 — 여기서 값이 바뀌면 원장이 바뀐 것이다
        assertThat(a02.rubricText()).contains("list_overbooked").contains("이현창").contains("김경민");
        assertThat(a02.rubricText()).as("절 머리표의 앵커(모집단 규칙)도 함께 간다")
                .contains("billable");
    }

    @Test
    @DisplayName("표 칸 수가 다르면 조용히 채우지 않고 던진다")
    void rejectsMalformedRow() {
        java.nio.file.Path broken;
        try {
            broken = java.nio.file.Files.createTempFile("ledger", ".md");
            java.nio.file.Files.writeString(broken, """
                    ## A. 가동률 (1) — 머리표
                    | ID | 화자 | 입력 |
                    |----|------|------|
                    | A-01 | 박재완 | 질문 |
                    """);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> Ledger.load(broken)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("칸 수");
    }
}
