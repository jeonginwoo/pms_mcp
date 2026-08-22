package kr.proten.pms.resource.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.NotImplementedException;
import kr.proten.pms.resource.service.UtilizationQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 가동률 API 웹 슬라이스 테스트 — 아직 산식이 없으므로 **골격 계약**만 본다.
 *
 * 여기서 잠그는 것 두 가지: ①`month`가 "yyyy-MM"으로 바인딩된다(형식이 틀리면
 * 400이라 나중에 조용히 바뀌면 안 된다) ②미구현은 500이 아니라 §7 봉투를 실은
 * **501 `NOT_IMPLEMENTED`** 다 — 호출자가 "고장"과 "아직 없음"을 구분할 수 있어야 한다.
 * 산식·모집단 규칙(C1-1·C1-3·C1-5)의 검증은 구현이 들어올 때 서비스 테스트가 맡는다.
 */
@WebMvcTest(UtilizationController.class)
class UtilizationControllerTest {
    private static final String CALLER_HEADER = "X-Caller-Person-Id";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UtilizationQueryService utilizationQueryService;

    @Test
    @DisplayName("골격 — 라우트는 붙어 있고 미구현은 501 NOT_IMPLEMENTED 봉투다")
    void find_whileScaffolded_returnsNotImplementedEnvelope() throws Exception {
        when(utilizationQueryService.find(anyLong(), any()))
                .thenThrow(new NotImplementedException("가동률 조회 (C1-1)"));

        mockMvc.perform(get("/api/utilization")
                        .param("month", "2026-08")
                        .header(CALLER_HEADER, "1"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("NOT_IMPLEMENTED"))
                // field 없는 오류는 키 자체가 빠진다 — 클라이언트가 null로 정규화한다
                .andExpect(jsonPath("$.error.field").doesNotExist());
    }

    @Test
    @DisplayName("month 형식이 yyyy-MM이 아니면 400 — 바인딩 계약을 잠근다")
    void find_withMalformedMonth_isBadRequest() throws Exception {
        mockMvc.perform(get("/api/utilization")
                        .param("month", "2026-8-1")
                        .header(CALLER_HEADER, "1"))
                .andExpect(status().isBadRequest());
    }
}
