package kr.proten.pmshost.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerTest {

    /** {"sub":"18"} 페이로드의 무서명 JWT — 컨트롤러는 sub 파싱 가능성만 본다 */
    private static final String TOKEN = "e30." + java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"sub\":\"18\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".sig";

    private ChatService chatService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService)).build();
    }

    @Test
    @DisplayName("Authorization 헤더 없으면 401 — 토큰 없는 챗은 없다")
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"내 가동률\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("sub를 읽을 수 없는 토큰은 401")
    void rejectsUnparseableToken() throws Exception {
        mockMvc.perform(post("/chat")
                        .header("Authorization", "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"내 가동률\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("빈 message는 400")
    void rejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/chat")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Bearer 토큰을 벗겨 서비스로 그대로 전달, 대화 id 기본값 = default")
    void passesTokenThrough() throws Exception {
        when(chatService.chat(eq("default"), eq("내 가동률"), eq(TOKEN))).thenReturn("답변");

        mockMvc.perform(post("/chat")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"내 가동률\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("default"))
                .andExpect(jsonPath("$.reply").value("답변"));

        verify(chatService).chat("default", "내 가동률", TOKEN);
    }

}
