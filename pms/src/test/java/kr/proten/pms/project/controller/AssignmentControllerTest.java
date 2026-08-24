package kr.proten.pms.project.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.project.service.AssignmentService;
import kr.proten.pms.project.service.dto.AssignmentView;
import kr.proten.pms.project.service.dto.CreateAssignmentCommand;
import kr.proten.pms.project.service.dto.UpdateAssignmentCommand;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 배정 API 웹 슬라이스 테스트 — EPIC B의 HTTP 경계.
 * 상태 코드(201/200)·경로 id가 명령에 실리는지·서비스 예외 → §7 에러 봉투
 * (403/404/409/422)만 본다. 권한·가시성 규칙은 서비스 단위 테스트의 몫이다.
 */
@WebMvcTest(AssignmentController.class)
class AssignmentControllerTest {
    private static final String CALLER_HEADER = "X-Caller-Person-Id";
    private static final long PROJECT_ID = 7L;
    private static final long ASSIGNMENT_ID = 31L;

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AssignmentService assignmentService;

    @Test
    @DisplayName("배정 — 201 + 경로의 프로젝트 id가 명령에 실린다 (B1-1)")
    void assign_validRequest_returnsCreated() throws Exception {
        when(assignmentService.assign(anyLong(), any(CreateAssignmentCommand.class)))
                .thenReturn(view());

        mockMvc.perform(post("/api/projects/" + PROJECT_ID + "/assignments")
                        .header(CALLER_HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "personId": 105,
                                  "role": "PARTICIPANT",
                                  "startDate": "2026-09-01",
                                  "monthlyMm": 0.5
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(ASSIGNMENT_ID))
                .andExpect(jsonPath("$.data.personName").value("김참여"));

        ArgumentCaptor<CreateAssignmentCommand> captor =
                ArgumentCaptor.forClass(CreateAssignmentCommand.class);
        verify(assignmentService).assign(Mockito.eq(13L), captor.capture());
        Assertions.assertThat(captor.getValue().projectId()).isEqualTo(PROJECT_ID);
        Assertions.assertThat(captor.getValue().role()).isEqualTo(ProjectRole.PARTICIPANT);
        Assertions.assertThat(captor.getValue().monthlyMm()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("배정 — M/M 미지정은 0으로 채운다 (A6-6 기본값과 같은 의미)")
    void assign_withoutMonthlyMm_defaultsToZero() throws Exception {
        when(assignmentService.assign(anyLong(), any(CreateAssignmentCommand.class)))
                .thenReturn(view());

        mockMvc.perform(post("/api/projects/" + PROJECT_ID + "/assignments")
                        .header(CALLER_HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personId\": 105, \"role\": \"PL\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateAssignmentCommand> captor =
                ArgumentCaptor.forClass(CreateAssignmentCommand.class);
        verify(assignmentService).assign(anyLong(), captor.capture());
        Assertions.assertThat(captor.getValue().monthlyMm()).isZero();
    }

    @Test
    @DisplayName("배정 — 참여자 id 누락은 경계에서 400, 서비스에 도달하지 않는다")
    void assign_withoutPersonId_isValidationError() throws Exception {
        mockMvc.perform(post("/api/projects/" + PROJECT_ID + "/assignments")
                        .header(CALLER_HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"PARTICIPANT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("personId"));
        verify(assignmentService, never()).assign(anyLong(), any());
    }

    @Test
    @DisplayName("배정 — 중복 배정은 409 DUPLICATE_ASSIGNMENT 봉투 (B1-2)")
    void assign_duplicate_isConflict() throws Exception {
        when(assignmentService.assign(anyLong(), any(CreateAssignmentCommand.class)))
                .thenThrow(new ConflictException(ErrorCode.DUPLICATE_ASSIGNMENT, "이미 배정된 인원입니다"));

        mockMvc.perform(post("/api/projects/" + PROJECT_ID + "/assignments")
                        .header(CALLER_HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personId\": 105, \"role\": \"PARTICIPANT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_ASSIGNMENT"));
    }

    @Test
    @DisplayName("배정 — role=PM은 422 INVALID_ROLE 봉투 (A6-7)")
    void assign_managerRole_isUnprocessable() throws Exception {
        when(assignmentService.assign(anyLong(), any(CreateAssignmentCommand.class)))
                .thenThrow(new UnprocessableException(ErrorCode.INVALID_ROLE,
                        "PM 지정은 PM 교체 경로로만 가능합니다"));

        mockMvc.perform(post("/api/projects/" + PROJECT_ID + "/assignments")
                        .header(CALLER_HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personId\": 105, \"role\": \"PM\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("INVALID_ROLE"));
    }

    @Test
    @DisplayName("배정 — PM이 아니면 403 FORBIDDEN 봉투 (B1-4)")
    void assign_byNonManager_isForbidden() throws Exception {
        when(assignmentService.assign(anyLong(), any(CreateAssignmentCommand.class)))
                .thenThrow(new ForbiddenException("담당자만 가능"));

        mockMvc.perform(post("/api/projects/" + PROJECT_ID + "/assignments")
                        .header(CALLER_HEADER, "104")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personId\": 105, \"role\": \"PARTICIPANT\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("수정 — 200 + 경로의 배정 id가 명령에 실린다 (B1-4)")
    void update_validRequest_passesAssignmentId() throws Exception {
        when(assignmentService.update(anyLong(), any(UpdateAssignmentCommand.class)))
                .thenReturn(view());

        mockMvc.perform(put("/api/assignments/" + ASSIGNMENT_ID)
                        .header(CALLER_HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-09-01",
                                  "endDate": "2026-11-30",
                                  "monthlyMm": 0.8,
                                  "version": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ASSIGNMENT_ID));

        ArgumentCaptor<UpdateAssignmentCommand> captor =
                ArgumentCaptor.forClass(UpdateAssignmentCommand.class);
        verify(assignmentService).update(Mockito.eq(13L), captor.capture());
        Assertions.assertThat(captor.getValue().assignmentId()).isEqualTo(ASSIGNMENT_ID);
        Assertions.assertThat(captor.getValue().version()).isEqualTo(2L);
    }

    @Test
    @DisplayName("수정 — version 누락은 경계에서 400")
    void update_withoutVersion_isValidationError() throws Exception {
        mockMvc.perform(put("/api/assignments/" + ASSIGNMENT_ID)
                        .header(CALLER_HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyMm\": 0.8}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("version"));
        verify(assignmentService, never()).update(anyLong(), any());
    }

    @Test
    @DisplayName("수정 — 없는 배정·가시성 밖은 같은 404 은닉 봉투")
    void update_unknownAssignment_isNotFound() throws Exception {
        when(assignmentService.update(anyLong(), any(UpdateAssignmentCommand.class)))
                .thenThrow(new NotFoundException());

        mockMvc.perform(put("/api/assignments/" + ASSIGNMENT_ID)
                        .header(CALLER_HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyMm\": 0.8, \"version\": 2}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("종료 — 200 + success:true, 본문 없이 배정 id만으로 처리한다 (B2-1)")
    void close_validRequest_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/assignments/" + ASSIGNMENT_ID)
                        .header(CALLER_HEADER, "13"))
                .andExpect(status().isOk());
        verify(assignmentService).close(13L, ASSIGNMENT_ID);
    }

    @Test
    @DisplayName("종료 — PM 배정 종료 거절은 422 INVALID_ROLE 봉투")
    void close_managerAssignment_isUnprocessable() throws Exception {
        Mockito.doThrow(new UnprocessableException(ErrorCode.INVALID_ROLE,
                        "PM 배정은 종료할 수 없습니다 — PM을 교체한 뒤 종료하세요"))
                .when(assignmentService).close(anyLong(), anyLong());

        mockMvc.perform(delete("/api/assignments/" + ASSIGNMENT_ID)
                        .header(CALLER_HEADER, "13"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("INVALID_ROLE"));
    }

    private AssignmentView view() {
        return new AssignmentView(
                ASSIGNMENT_ID,
                105L,
                "김참여",
                true,
                ProjectRole.PARTICIPANT,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 12, 31),
                0.5,
                2L);
    }
}
