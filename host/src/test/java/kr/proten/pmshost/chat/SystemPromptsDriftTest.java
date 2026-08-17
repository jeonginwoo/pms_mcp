package kr.proten.pmshost.chat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시스템 프롬프트 사본이 정본(docs/구현_노트.md §4-1)과 일치함을 고정한다.
 * 불일치 시 문서가 이긴다 — 코드를 문서에 맞춰라(§4-2 버전 규칙).
 */
class SystemPromptsDriftTest {

    @Test
    @DisplayName("SystemPrompts.PMS_ASSISTANT == 구현_노트 §4-1 전문 (문서가 정본)")
    void promptMatchesDocument() throws Exception {
        String doc = Files.readString(Path.of("..", "docs", "구현_노트.md"));

        int section = doc.indexOf("### 4-1");
        assertThat(section).as("구현_노트에 §4-1 절이 있어야 한다").isPositive();
        int fenceOpen = doc.indexOf("```", section);
        int start = doc.indexOf('\n', fenceOpen) + 1;
        int end = doc.indexOf("\n```", start);
        String docPrompt = doc.substring(start, end);

        assertThat(normalize(SystemPrompts.PMS_ASSISTANT)).isEqualTo(normalize(docPrompt));
    }

    private static String normalize(String s) {
        return s.lines().map(String::stripTrailing).reduce("", (a, b) -> a + "\n" + b).trim();
    }

}
