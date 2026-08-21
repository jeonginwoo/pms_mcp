package kr.proten.pms.person.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.proten.pms.person.service.MeQueryService;
import kr.proten.pms.person.service.dto.MeView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 내 계정 API 웹 슬라이스 테스트 — 화면이 UI를 정리할 때 쓰는 플래그가 그대로 나가는지.
 */
@WebMvcTest(MeController.class)
class MeControllerTest {
    private static final String CALLER_HEADER = "X-Caller-Person-Id";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private MeQueryService meQueryService;

    @Test
    @DisplayName("내 계정 — 신원과 권한 그룹 플래그가 함께 나간다")
    void me_returnsIdentityAndFlags() throws Exception {
        when(meQueryService.me(1L)).thenReturn(new MeView(
                1L, "박재완", "경영관리팀", "대표이사", "관리자", "COMPANY",
                true, true, true, true));

        mockMvc.perform(get("/api/me").header(CALLER_HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("박재완"))
                .andExpect(jsonPath("$.group").value("관리자"))
                .andExpect(jsonPath("$.visibilityScope").value("COMPANY"))
                .andExpect(jsonPath("$.createProject").value(true))
                .andExpect(jsonPath("$.manageOrg").value(true));
    }

    @Test
    @DisplayName("호출자 헤더가 없으면 401 — 다른 라우트와 같은 수렴")
    void me_withoutCaller_isUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }
}
