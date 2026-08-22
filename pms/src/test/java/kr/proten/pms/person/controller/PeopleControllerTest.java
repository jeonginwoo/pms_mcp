package kr.proten.pms.person.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.person.PersonRef;
import kr.proten.pms.person.service.PersonService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 인력 조회 API 웹 슬라이스 테스트.
 * 검증 대상은 HTTP 경계뿐이다 — 라우트·호출자 식별 헤더·서비스 위임·에러 봉투 변환.
 * 가시성 판정 자체는 서비스 단위 테스트가 담당한다(PersonQueryServiceImplTest).
 */
@WebMvcTest(PeopleController.class)
class PeopleControllerTest {
    private static final String CALLER_HEADER = "X-Caller-Person-Id";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private PersonService personService;

    @Test
    @DisplayName("목록 — 호출자 헤더의 personId로 서비스에 위임한다")
    void list_delegatesWithCallerFromHeader() throws Exception {
        when(personService.listVisible(102L)).thenReturn(List.of(
                new PersonRef(102L, "팀장", "SI팀", "수석"),
                new PersonRef(103L, "팀원", "SI팀", "주임")));

        mockMvc.perform(get("/api/people").header(CALLER_HEADER, "102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("팀장"))
                .andExpect(jsonPath("$.data[1].orgUnit").value("SI팀"));
    }

    @Test
    @DisplayName("단건 — 경로 변수와 호출자를 함께 넘긴다")
    void get_passesPathVariableAndCaller() throws Exception {
        when(personService.getPerson(102L, 103L))
                .thenReturn(new PersonRef(103L, "팀원", "SI팀", "주임"));

        mockMvc.perform(get("/api/people/103").header(CALLER_HEADER, "102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(103))
                .andExpect(jsonPath("$.data.grade").value("주임"));
    }

    @Test
    @DisplayName("단건 — 404 은닉이 §7 에러 봉투로 나간다")
    void get_notFound_returnsErrorEnvelope() throws Exception {
        when(personService.getPerson(102L, 999L)).thenThrow(new NotFoundException());

        mockMvc.perform(get("/api/people/999").header(CALLER_HEADER, "102"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("해당 데이터 없음"))
                .andExpect(jsonPath("$.error.traceId").exists());
    }

    @Test
    @DisplayName("호출자 헤더가 없으면 401 — 서비스에 도달하지 않는다")
    void request_withoutCallerHeader_isUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/people"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

        verify(personService, never()).listVisible(anyLong());
    }

    @Test
    @DisplayName("호출자 헤더가 숫자가 아니면 401 — 부재와 같은 응답으로 수렴한다")
    void request_withMalformedCallerHeader_isUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/people").header(CALLER_HEADER, "not-a-number"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }
}
