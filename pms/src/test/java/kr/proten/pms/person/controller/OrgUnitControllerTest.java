package kr.proten.pms.person.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.person.service.OrgUnitService;
import kr.proten.pms.person.service.dto.OrgUnitView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 조직 트리 API 웹 슬라이스 테스트 — 상태 코드와 §7 에러 봉투(403·409)만 본다.
 */
@WebMvcTest(OrgUnitController.class)
class OrgUnitControllerTest {
    private static final String CALLER_HEADER = "X-Caller-Person-Id";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private OrgUnitService orgUnitService;

    @Test
    @DisplayName("목록 — 노드별 인원·프로젝트 수와 삭제 가능 여부가 그대로 나간다")
    void list_returnsUnitsWithCounts() throws Exception {
        // 세 번째 노드는 인원도 하위 노드도 없는데 프로젝트만 있다 — 퇴사한 PM이 남긴
        // 모양이고(부록 A 조직 트리 · §12), 화면이 그 수를 그릴 수 있어야 한다
        when(orgUnitService.list(1L)).thenReturn(List.of(
                new OrgUnitView(1L, null, "(주)프로텐", 0, 6, 0, false),
                new OrgUnitView(99L, 1L, "빈팀", 0, 0, 0, true),
                new OrgUnitView(98L, 1L, "해산팀", 0, 0, 14, false)));

        mockMvc.perform(get("/api/org-units").header(CALLER_HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[1].name").value("빈팀"))
                .andExpect(jsonPath("$.data[1].projectCount").value(0))
                .andExpect(jsonPath("$.data[1].deletable").value(true))
                .andExpect(jsonPath("$.data[2].projectCount").value(14))
                .andExpect(jsonPath("$.data[2].deletable").value(false));
    }

    @Test
    @DisplayName("목록 — 관리 권한이 없으면 403 봉투 (E2-4)")
    void list_withoutManageOrg_isForbidden() throws Exception {
        when(orgUnitService.list(anyLong()))
                .thenThrow(new ForbiddenException("사용자·조직 관리 권한이 없습니다"));

        mockMvc.perform(get("/api/org-units").header(CALLER_HEADER, "103"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("삭제 — 200 + success:true (본문 없는 성공도 같은 봉투다)")
    void delete_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/org-units/99").header(CALLER_HEADER, "1"))
                .andExpect(status().isOk());
        verify(orgUnitService).delete(1L, 99L);
    }

    @Test
    @DisplayName("삭제 — 비어 있지 않은 노드는 409 IN_USE 봉투 (E3-3)")
    void delete_nonEmptyUnit_isConflict() throws Exception {
        doThrow(new ConflictException(ErrorCode.IN_USE, "소속 인원 4명·하위 조직 0개가 있어 삭제할 수 없습니다"))
                .when(orgUnitService).delete(anyLong(), anyLong());

        mockMvc.perform(delete("/api/org-units/3").header(CALLER_HEADER, "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IN_USE"));
    }
}
