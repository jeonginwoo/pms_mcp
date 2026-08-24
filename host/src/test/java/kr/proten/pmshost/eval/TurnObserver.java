package kr.proten.pmshost.eval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;

/**
 * 한 턴이 남긴 <b>증거</b>를 모은다 — 모델이 부른 도구·인자와, host가 본문을 못 찾은
 * 사건(EMPTY_REPLY). 응답 문장만으로는 기대 도구 흐름을 채점할 수 없고, F5(도구·
 * 파라미터 오선택)는 인자를 봐야 판정된다.
 *
 * <p><b>도구를 더하지 않고 얻은 관측 지점이다</b>(2026-08-24 실서버 관통에서 발견):
 * MCP SDK가 DEBUG로 이미 {@code CallToolRequest[name=…, arguments={…}]}를 찍고,
 * {@code ChatService}는 본문을 못 찾았을 때 {@code EMPTY_REPLY} 키로 warn한다 —
 * 그 키는 <b>러너가 갈라 보라고</b> 고정 문자열로 박아 둔 것이다. host 코드에 후킹
 * 지점을 새로 뚫으면 프로덕션 경로가 eval 때문에 바뀌고, 채점 대상이 실제로 배포되는
 * 물건과 갈린다.
 *
 * <p>실측은 로그 파일을 잘라 읽었지만(러너 밖에서 관측했으므로) 여기서는 <b>같은
 * 로거에 appender를 붙여</b> in-process로 받는다 — 파일 오프셋도, 케이스 경계를
 * 시간으로 맞추는 일도 없어진다.
 *
 * <p>형식이 바뀔 수 있으므로 파싱에 실패해도 <b>원문을 버리지 않는다</b>: 관측이
 * 조용히 비면 "도구를 안 불렀다"로 오독되어 채점을 오염시킨다.
 */
final class TurnObserver implements AutoCloseable {

    /** SDK가 이 이름 아래에서 전송 계층 로그를 찍는다 */
    private static final String MCP_LOGGER = "io.modelcontextprotocol";
    private static final String CHAT_LOGGER = "kr.proten.pmshost.chat";

    /** 모델의 무응답(F6)과 "host가 본문을 못 찾음"을 가르는 표식 (ChatService) */
    static final String EMPTY_REPLY_KEY = "EMPTY_REPLY";

    private static final String MARKER = "CallToolRequest[";
    private static final Pattern NAME = Pattern.compile("^name=([^,]+),");

    private final Tap mcp;
    private final Tap chat;

    TurnObserver() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        // SDK DEBUG는 채집용이지 사람이 볼 것이 아니다 — additive를 끊지 않으면
        // 콘솔이 전송 계층 로그로 뒤덮여 러너의 진행 로그가 묻힌다
        this.mcp = new Tap(context, MCP_LOGGER, Level.DEBUG, false);
        this.chat = new Tap(context, CHAT_LOGGER, Level.WARN, true);
    }

    /** 지금까지 관측한 도구 호출을 돌려주고 비운다 (턴 경계에서 부른다) */
    List<ToolCall> drainToolCalls() {
        List<ToolCall> calls = new ArrayList<>();
        for (String message : mcp.drain()) {
            int at = message.indexOf(MARKER);
            if (at < 0) {
                continue;
            }
            String fragment = message.substring(at);
            calls.add(parse(fragment));
        }

        return calls;
    }

    /**
     * {@code CallToolRequest[name=…, arguments={…}, meta={…}]} 한 건을 뜯는다.
     *
     * <p>정규식으로 끝을 잡지 않는다 — 이 레코드는 <b>바깥 레코드 안에</b> 실려 오고
     * (`JSONRPCRequest[… params=CallToolRequest[…]]`) 인자 자체가 중괄호를 품는다.
     * 줄 끝을 기준으로 자르면 뒤의 {@code meta={}]}가 인자에 섞여 들어와, 인자가
     * 채점 근거인 F5 판정이 오염된다(첫 회차에서 실제로 그렇게 나왔다).
     * 대괄호 짝을 세어 레코드의 진짜 끝을 찾는다.
     */
    static ToolCall parse(String fragment) {
        int depth = 0;
        int end = -1;
        for (int i = MARKER.length() - 1; i < fragment.length(); i++) {
            char ch = fragment.charAt(i);
            if (ch == '[') {
                depth++;
            } else if (ch == ']' && --depth == 0) {
                end = i;
                break;
            }
        }
        if (end < 0) {
            return new ToolCall(null, null, fragment);
        }

        String inner = fragment.substring(MARKER.length(), end);
        Matcher name = NAME.matcher(inner);
        int args = inner.indexOf("arguments=");
        if (!name.find() || args < 0) {
            return new ToolCall(null, null, fragment);
        }
        String arguments = inner.substring(args + "arguments=".length());
        int meta = arguments.lastIndexOf(", meta=");
        if (meta >= 0) {
            arguments = arguments.substring(0, meta);
        }

        return new ToolCall(name.group(1).trim(), arguments.trim(), fragment);
    }

    /** host가 본문을 못 찾아 안내 문구로 대체한 사건 — 모델의 회피성 무응답과 다르다 */
    List<String> drainEmptyReplies() {
        return chat.drain().stream().filter(m -> m.contains(EMPTY_REPLY_KEY)).toList();
    }

    @Override
    public void close() {
        mcp.close();
        chat.close();
    }

    /**
     * @param name      도구명 — 파싱 실패 시 null
     * @param arguments 인자 원문(JSON 유사 문자열) — F5 판정의 근거
     * @param raw       로그 원문. 파싱이 형식 변화로 깨져도 증거는 남는다
     */
    record ToolCall(String name, String arguments, String raw) {
    }

    /** 한 로거에 붙은 수집기 — 원래 레벨은 되돌린다 */
    private static final class Tap extends AppenderBase<ILoggingEvent> implements AutoCloseable {

        private final List<String> messages = Collections.synchronizedList(new ArrayList<>());
        private final ch.qos.logback.classic.Logger logger;
        private final Level originalLevel;
        private final boolean originalAdditive;

        Tap(LoggerContext context, String loggerName, Level level, boolean additive) {
            this.logger = context.getLogger(loggerName);
            this.originalLevel = logger.getLevel();
            this.originalAdditive = logger.isAdditive();
            logger.setLevel(level);
            logger.setAdditive(additive);
            setContext(context);
            start();
            logger.addAppender(this);
        }

        @Override
        protected void append(ILoggingEvent event) {
            String message = event.getFormattedMessage();
            if (message != null) {
                messages.add(message);
            }
        }

        List<String> drain() {
            synchronized (messages) {
                List<String> drained = List.copyOf(messages);
                messages.clear();

                return drained;
            }
        }

        @Override
        public void close() {
            logger.detachAppender(this);
            stop();
            logger.setLevel(originalLevel);
            logger.setAdditive(originalAdditive);
        }
    }
}
