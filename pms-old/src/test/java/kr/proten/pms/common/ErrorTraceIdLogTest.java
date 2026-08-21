package kr.proten.pms.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import kr.proten.pms.common.internal.web.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * traceId 봉투-로그 상관 검증 (conventions/java-spring.md §4 "traceId must trace") —
 * 응답 봉투의 traceId가 서버 로그에도 남아 사용자 보고를 로그 라인과 상관시킬 수
 * 있어야 한다. 봉투 생성 지점 2곳(전역 핸들러·보안 체인 401)을 각각 관통한다.
 * H2 사용 — 로그 기록 의미론 검증이라 SQL 방언 무관 (conventions §8).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorTraceIdLogTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    // 테스트가 부착한 (로거, 어펜더) — 종료 시 분리
    private Logger attachedLogger;
    private ListAppender<ILoggingEvent> appender;

    private ListAppender<ILoggingEvent> attach(String loggerName) {
        attachedLogger = (Logger) LoggerFactory.getLogger(loggerName);
        appender = new ListAppender<>();
        appender.start();
        attachedLogger.addAppender(appender);

        return appender;
    }

    @AfterEach
    void detach() {
        if (attachedLogger != null) {
            attachedLogger.detachAppender(appender);
        }
    }

    private String traceIdOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("error").get("traceId").asString();
    }

    @Test
    @DisplayName("전역 핸들러 경로(로그인 실패 401) — 봉투 traceId가 로그에 남는다")
    void apiExceptionEnvelope_traceIdAppearsInLog() throws Exception {
        var events = attach(GlobalExceptionHandler.class.getName());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@proten.co.kr\",\"password\":\"proten1!\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        String traceId = traceIdOf(result);

        assertThat(events.list)
                .anyMatch(event -> event.getFormattedMessage().contains(traceId));
    }

    @Test
    @DisplayName("보안 체인 경로(무토큰 401) — 봉투 traceId가 로그에 남는다")
    void securityChain401Envelope_traceIdAppearsInLog() throws Exception {
        // ApiSecurityConfig는 패키지 프라이빗 — 로거 이름 문자열로 부착
        var events = attach("kr.proten.pms.identity.internal.web.ApiSecurityConfig");

        MvcResult result = mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        String traceId = traceIdOf(result);

        assertThat(events.list)
                .anyMatch(event -> event.getFormattedMessage().contains(traceId));
    }
}
