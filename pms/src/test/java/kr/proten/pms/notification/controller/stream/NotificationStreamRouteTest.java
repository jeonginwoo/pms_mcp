package kr.proten.pms.notification.controller.stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.proten.pms.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 스트림 라우트 (AC F1-4 · PRD-pms §7).
 *
 * <p><b>이 클래스가 없어서 보안 결정이 문서로만 서 있었다</b>(2026-08-25 리뷰 지적):
 * 레지스트리와 리졸버는 단위로 잠겨 있었지만, <b>라우트가 실제로 토큰 없이 거절하는지</b>는
 * 아무것도 증명하지 않았다. 앱에서 유일하게 쿼리로 인증하는 자리라 그 공백이 특히 나빴다.
 *
 * <p>{@code produces = text/event-stream}인 핸들러에서 예외가 <b>정말 401 봉투로</b> 나가는지도
 * 여기서 실측한다 — 브라우저는 {@code Accept: text/event-stream}을 보내는데 오류 응답은
 * JSON이라, 협상이 어긋나면 401이 아니라 406이 될 수 있다(리뷰가 제기한 의심).
 */
@WebMvcTest(controllers = NotificationStreamController.class)
@Import({StreamCallerConfig.class, NotificationStream.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "pms.auth.enabled=false")
class NotificationStreamRouteTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("F1-4 — access_token이 없으면 401이다 (406이 아니다)")
    void withoutTokenIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/notifications/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("F1-4 — 비숫자 증명도 같은 401로 수렴한다")
    void malformedProofConvergesOnTheSame401() throws Exception {
        mockMvc.perform(get("/api/notifications/stream")
                        .param("access_token", "나")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("F1-4 — 열리면 SSE 응답이고 프록시 버퍼링을 끈다")
    void openStreamDisablesProxyBuffering() throws Exception {
        mockMvc.perform(get("/api/notifications/stream")
                        .param("access_token", "13")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                // 버퍼링되면 "즉시 푸시"가 죽는다 (구현 노트 §6)
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(header().string("Cache-Control", "no-cache, no-transform"));
    }
}
