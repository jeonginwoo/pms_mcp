package kr.proten.pmshost.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 도구 호출 파싱 — <b>인자가 채점 근거</b>라서(F5 도구·파라미터 오선택) 여기가 틀리면
 * 회차 기록이 조용히 오염된다. 첫 회차에서 실제로 `meta={}]`가 인자에 섞여 나왔다.
 * 입력은 실 회차에서 그대로 가져온 SDK 로그 줄이다.
 */
class TurnObserverParseTest {

    /** 2026-08-24 A-05 회차의 실 로그 (io.modelcontextprotocol DEBUG) */
    private static final String REAL = "Sending message JSONRPCRequest[jsonrpc=2.0, "
            + "method=tools/call, id=69d6d7f1-2, params=CallToolRequest[name=get_utilization, "
            + "arguments={month=2026-08, scope=ME}, meta={}]]";

    @Test
    @DisplayName("바깥 레코드에 실려 와도 도구명과 인자만 뜯는다 — meta는 인자가 아니다")
    void extractsNameAndArgumentsOnly() {
        TurnObserver.ToolCall call = TurnObserver.parse(REAL.substring(REAL.indexOf("CallToolRequest[")));

        assertThat(call.name()).isEqualTo("get_utilization");
        assertThat(call.arguments()).isEqualTo("{month=2026-08, scope=ME}");
    }

    @Test
    @DisplayName("인자가 중괄호를 품어도 잘리지 않는다")
    void keepsNestedArguments() {
        String fragment = "CallToolRequest[name=update_progress, "
                + "arguments={projectId=347, percent=20, confirmed=false, extra={a=1, b={c=2}}}, "
                + "meta={}]";

        TurnObserver.ToolCall call = TurnObserver.parse(fragment);

        assertThat(call.name()).isEqualTo("update_progress");
        assertThat(call.arguments())
                .isEqualTo("{projectId=347, percent=20, confirmed=false, extra={a=1, b={c=2}}}");
    }

    @Test
    @DisplayName("형식이 바뀌어 못 뜯어도 원문은 남긴다 — 조용히 비면 '호출 없음'으로 오독된다")
    void keepsRawWhenShapeChanges() {
        TurnObserver.ToolCall call = TurnObserver.parse("CallToolRequest[뭔가 다른 모양");

        assertThat(call.name()).isNull();
        assertThat(call.raw()).isEqualTo("CallToolRequest[뭔가 다른 모양");
    }
}
