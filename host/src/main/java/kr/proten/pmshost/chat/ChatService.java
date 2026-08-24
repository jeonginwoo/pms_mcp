package kr.proten.pmshost.chat;

import java.time.Clock;
import java.time.LocalDate;

import io.modelcontextprotocol.client.McpSyncClient;
import kr.proten.pmshost.mcp.PmsMcpConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.stereotype.Service;

/**
 * 에이전트 루프 (구현_노트 §3-3) — 도구 호출·결과 반영·재호출은 ChatClient가
 * 내장한다. 여기서 코드로 강제하는 것은 프롬프트만으로 못 지키는 것들:
 * 대화 이력 10턴(FR-AI-01)·오늘 날짜와 호출자 identity 주입·토큰 패스스루.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** FR-AI-01 "최대 10턴" — 턴 = 질문-응답 1회이므로 메시지 20개 */
    static final int MAX_MEMORY_MESSAGES = 20;

    /**
     * 모델이 본문 없이 턴을 끝냈을 때 사용자에게 대신 주는 문구.
     * 2026-08-24 실서버 관통에서 실제로 재현된 상황이라 자리를 만들어 뒀다 —
     * 빈 문자열을 그대로 흘리면 챗 위젯에 아무것도 뜨지 않아 사용자가 원인을
     * 알 수 없다. 프롬프트 규칙 4(실패 사실과 재시도 여부 안내)와 같은 취지다.
     */
    static final String EMPTY_REPLY_NOTICE = "답변을 생성하지 못했습니다. 질문을 조금 더 구체적으로 주시거나 다시 시도해 주세요.";

    private final ChatClient chatClient;
    private final PmsMcpConnector pmsMcp;
    private final Clock clock;

    public ChatService(ChatClient.Builder chatClientBuilder, PmsMcpConnector pmsMcp, Clock clock) {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(MAX_MEMORY_MESSAGES)
                .build();
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
        this.pmsMcp = pmsMcp;
        this.clock = clock;
    }

    public String chat(String conversationId, String userMessage, String userToken) {
        // 메모리 키에 화자(sub)를 앞세워 사용자 간 대화 맥락이 섞이지 않게 한다.
        // sub를 못 읽으면 공용 키로 뭉치는 대신 거절 — fail-closed
        String sub = TokenHints.claim(userToken, "sub")
                .orElseThrow(() -> new IllegalArgumentException("토큰에서 사용자(sub)를 읽을 수 없습니다"));
        String memoryKey = sub + ":" + conversationId;
        try (McpSyncClient pms = pmsMcp.connect(userToken)) {
            var tools = SyncMcpToolCallbackProvider.builder()
                    .mcpClients(pms)
                    // 시스템 프롬프트가 도구명을 그대로 참조한다 — 접두사 금지
                    .toolNamePrefixGenerator(McpToolNamePrefixGenerator.noPrefix())
                    .build();
            ChatResponse response = chatClient.prompt()
                    .system(SystemPrompts.PMS_ASSISTANT + contextBlock(userToken))
                    .user(userMessage)
                    .tools(tools)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryKey))
                    .call()
                    .chatResponse();

            return replyOf(response, memoryKey);
        }
    }

    /**
     * 응답 본문 = **사고 블록을 걷어낸 뒤 마지막에 오는 본문 generation**.
     * `.content()`(= 첫 generation)를 쓰면 답을 버리는 경우가 있다 — 2026-08-24
     * 실서버 관통에서 재현했다(generations=2 · [0] 0자 · [1] 295자 ·
     * finishReason=end_turn · completionTokens=606 — 모델은 정상 답을 냈다).
     * 목업 시절에는 응답이 작아 generation이 하나였기 때문에 드러나지 않았다.
     *
     * 왜 "전부 이어붙이기"가 아닌가 — `AnthropicChatModel.buildGenerations` 실측:
     * **TEXT 블록은 전부 하나로 합쳐져 맨 뒤 generation**이 되고, 앞자리 generation은
     * `thinking`·`redacted_thinking`뿐이다(각각 `signature`·`data` 속성을 달고 온다).
     * 그래서 이어붙이면 사고 과정이 사용자 답변에 섞여 나간다 — 실측 때 [0]이 빈
     * 블록이라 겉으로 멀쩡해 보였을 뿐이다. 사고 블록은 표현 계층에서 버린다.
     *
     * 빈 응답 자리는 남긴다(fail-visible): 빈 문자열을 그대로 흘리면 챗 위젯에
     * 아무것도 뜨지 않아 사용자가 원인을 알 수 없다. 프롬프트 규칙 4와 같은 취지.
     */
    private static String replyOf(ChatResponse response, String memoryKey) {
        String text = response == null ? "" : response.getResults().stream()
                .filter(generation -> !isThinking(generation.getOutput()))
                .map(generation -> generation.getOutput().getText())
                .filter(t -> t != null && !t.isBlank())
                // 본문은 맨 뒤에 온다 — 앞자리가 남아 있어도 그것을 답으로 쓰지 않는다
                .reduce((earlier, later) -> later)
                .orElse("");
        if (!text.isBlank()) {
            return text;
        }

        // 로그 키를 고정 문자열로 둔다 — eval 러너가 "모델의 회피성 무응답(F6)"과
        // "host가 본문을 못 찾은 것"을 갈라 봐야 하고, 사용자에게 나가는 문구는
        // 평범한 한국어라 그것만으로는 구분되지 않는다(다음 항목의 채점 요구사항)
        log.warn("EMPTY_REPLY 모델이 본문 없이 응답했다 — 대화 {} · finishReason={} · usage={}", memoryKey,
                response == null || response.getResult() == null
                        ? "(응답 없음)" : response.getResult().getMetadata().getFinishReason(),
                response == null ? "(응답 없음)" : response.getMetadata().getUsage());

        return EMPTY_REPLY_NOTICE;
    }

    /**
     * 사고 블록 판별 — Spring AI가 `thinking`에는 `signature`, `redacted_thinking`에는
     * `data`를 속성으로 실어 보내는 것이 유일한 표식이다(본문 generation은 둘 다 없다).
     */
    private static boolean isThinking(AssistantMessage message) {
        return message.getMetadata().containsKey("signature")
                || message.getMetadata().containsKey("data");
    }

    /**
     * "이번 달"류 상대 시점 해석의 기준 날짜 + 호출자 identity. identity는
     * whoami 호출을 아끼는 힌트일 뿐 권한 판정 근거가 아니다(판정은 서버).
     */
    private String contextBlock(String userToken) {
        StringBuilder sb = new StringBuilder("\n[컨텍스트]\n- 오늘 날짜: ").append(LocalDate.now(clock));
        TokenHints.claim(userToken, "name")
                .or(() -> TokenHints.claim(userToken, "sub"))
                .ifPresent(who -> sb.append("\n- 대화 상대(사용자): ").append(who)
                        .append(" — 참고용 힌트이며 권한·가시성 판단은 서버가 한다"));

        return sb.toString();
    }

}
