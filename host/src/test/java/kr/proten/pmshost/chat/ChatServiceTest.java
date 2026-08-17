package kr.proten.pmshost.chat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import kr.proten.pmshost.mcp.PmsMcpConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    /** eval 기준일(2026-08)과 정합 — "이번 달" 해석 기준 */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-13T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final List<Prompt> capturedPrompts = new ArrayList<>();

    private final ChatModel stubModel = new ChatModel() {
        @Override
        public ChatResponse call(Prompt prompt) {
            capturedPrompts.add(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("스텁 응답"))));
        }
    };

    private PmsMcpConnector connector;
    private McpSyncClient mcpClient;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        connector = mock(PmsMcpConnector.class);
        mcpClient = mock(McpSyncClient.class);
        when(connector.connect(org.mockito.ArgumentMatchers.anyString())).thenReturn(mcpClient);
        when(mcpClient.listTools())
                .thenReturn(new McpSchema.ListToolsResult(List.of(), null, null));
        chatService = new ChatService(ChatClient.builder(stubModel), connector, FIXED_CLOCK);
    }

    /** HS256 목업 토큰과 같은 구조의 무서명 JWT — 힌트 클레임(sub·name)만 본다 */
    private static String tokenOf(String sub, String name) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(
                ("{\"sub\":\"" + sub + "\",\"name\":\"" + name + "\",\"aud\":\"pms\"}")
                        .getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".fake-sig";
    }

    @Test
    @DisplayName("시스템 메시지 = 프롬프트 v0.2 + 오늘 날짜 + 화자 identity 힌트 (구현_노트 §3-3)")
    void systemMessageCarriesPromptDateAndIdentity() {
        String reply = chatService.chat("c1", "내 가동률 알려줘", tokenOf("18", "신현랑"));

        assertThat(reply).isEqualTo("스텁 응답");
        Message system = capturedPrompts.get(0).getInstructions().get(0);
        assertThat(system.getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(system.getText())
                .contains("너는 사내 PMS")
                .contains("오늘 날짜: 2026-08-13")
                .contains("신현랑");
    }

    @Test
    @DisplayName("같은 화자·같은 대화 id는 이전 턴이 맥락으로 실린다 (FR-AI-01)")
    void memoryKeepsTurnsWithinSameConversation() {
        String token = tokenOf("18", "신현랑");
        chatService.chat("c1", "첫 질문", token);
        chatService.chat("c1", "후속 질문", token);

        List<Message> second = capturedPrompts.get(1).getInstructions();
        assertThat(second.stream().map(Message::getText))
                .anyMatch(t -> t != null && t.contains("첫 질문"))
                .anyMatch(t -> t != null && t.contains("스텁 응답"));
    }

    @Test
    @DisplayName("화자(sub)가 다르면 같은 대화 id라도 맥락이 섞이지 않는다")
    void memoryIsolatedBetweenUsers() {
        chatService.chat("c1", "신현랑의 질문", tokenOf("18", "신현랑"));
        chatService.chat("c1", "정태휘의 질문", tokenOf("3", "정태휘"));

        List<Message> secondUsers = capturedPrompts.get(1).getInstructions();
        assertThat(secondUsers.stream().map(Message::getText))
                .noneMatch(t -> t != null && t.contains("신현랑의 질문"));
    }

    @Test
    @DisplayName("10턴(메시지 20개) 초과 시 가장 오래된 턴부터 밀려난다 (FR-AI-01)")
    void memoryEvictsBeyondTenTurns() {
        String token = tokenOf("18", "신현랑");
        for (int i = 1; i <= 12; i++) {
            chatService.chat("c1", "질문-%02d".formatted(i), token);
        }

        // 11턴 저장 시점에 20개 초과 → 1턴(질문-01·응답)이 밀려나 12번째 프롬프트에 없다
        List<Message> twelfth = capturedPrompts.get(11).getInstructions();
        assertThat(twelfth.stream().map(Message::getText))
                .noneMatch(t -> t != null && t.contains("질문-01"))
                .anyMatch(t -> t != null && t.contains("질문-11"));
    }

    @Test
    @DisplayName("sub를 못 읽는 토큰은 거절 — 공용 메모리 키로 뭉치지 않는다(fail-closed)")
    void rejectsUnparseableToken() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> chatService.chat("c1", "질문", "not-a-jwt"));
    }

    @Test
    @DisplayName("MCP 클라이언트는 대화 단위 생성·응답 후 close (구현_노트 §3-2)")
    void mcpClientClosedAfterChat() {
        chatService.chat("c1", "질문", tokenOf("18", "신현랑"));

        verify(mcpClient).close();
    }

}
