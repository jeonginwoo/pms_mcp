package com.proten.pms.common.internal.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.proten.pms.common.internal.application.HealthQueryService;
import com.proten.pms.common.internal.application.HealthStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 헬스체크 REST 어댑터 슬라이스 테스트 — 애플리케이션 서비스 위임과 상태 코드 매핑 검증.
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HealthQueryService healthQueryService;

    @Test
    @DisplayName("DB가 정상이면 200과 UP 본문을 반환한다")
    void health_dbUp_returns200() throws Exception {
        // Given
        given(healthQueryService.check()).willReturn(new HealthStatus("UP", true));

        // When & Then
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value(true));
    }

    @Test
    @DisplayName("DB 왕복이 실패하면 503과 DOWN 본문을 반환한다")
    void health_dbDown_returns503() throws Exception {
        // Given
        given(healthQueryService.check()).willReturn(new HealthStatus("DOWN", false));

        // When & Then
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.database").value(false));
    }
}
