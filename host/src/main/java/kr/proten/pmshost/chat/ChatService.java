package kr.proten.pmshost.chat;

import java.time.Clock;
import java.time.LocalDate;

import io.modelcontextprotocol.client.McpSyncClient;
import kr.proten.pmshost.mcp.PmsMcpConnector;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
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

    /** FR-AI-01 "최대 10턴" — 턴 = 질문-응답 1회이므로 메시지 20개 */
    static final int MAX_MEMORY_MESSAGES = 20;

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
            return chatClient.prompt()
                    .system(SystemPrompts.PMS_ASSISTANT + contextBlock(userToken))
                    .user(userMessage)
                    .tools(tools)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryKey))
                    .call()
                    .content();
        }
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
