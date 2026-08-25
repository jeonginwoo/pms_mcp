package kr.proten.pms.project.controller;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.LocalDate;
import java.util.List;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.StaleVersionException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.project.HandoverSpec;
import kr.proten.pms.project.service.ProjectCommandService;
import kr.proten.pms.project.service.ProjectLifecycleService;
import kr.proten.pms.project.service.ProjectQueryService;
import kr.proten.pms.project.service.dto.AssignmentView;
import kr.proten.pms.project.service.dto.CreateProjectCommand;
import kr.proten.pms.project.service.dto.EditProjectCommand;
import kr.proten.pms.project.service.dto.ProgressUpdateResult;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.dto.ProjectSummary;
import kr.proten.pms.project.service.dto.UpdateProgressCommand;
import kr.proten.pms.project.service.entity.Engagement;
import kr.proten.pms.project.service.entity.ProjectPhase;
import kr.proten.pms.project.service.entity.ProjectRole;
import kr.proten.pms.project.ProjectStatus;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
/**
 * 프로젝트 API 웹 슬라이스 테스트.
 * 검증 대상은 HTTP 경계다 — 상태 코드(201/200)·page 봉투 형태·요청 본문 형식 검증
 * (400)·서비스 예외 → §7 에러 봉투 매핑(403/404/409/422). 권한·가시성·2단계 확인의
 * 규칙 자체는 서비스 단위 테스트가 담당한다.
 */
@WebMvcTest(ProjectController.class)
class ProjectControllerTest {
    private static final String CALLER_HEADER = "X-Caller-Person-Id";
    private static final long PROJECT_ID = 7L;
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ProjectCommandService projectCommandService;
    @MockitoBean
    private ProjectQueryService projectQueryService;
    @MockitoBean
    private ProjectLifecycleService projectLifecycleService;
    @Test
    @DisplayName("생성 — 201 + 생성된 상세, 요청 본문이 명령으로 옮겨진다")
    void create_validRequest_returnsCreated() throws Exception {
        when(projectCommandService.create(anyLong(), any(CreateProjectCommand.class)))
                .thenReturn(detail());
        mockMvc.perform(post("/api/projects")
                        .header(CALLER_HEADER, "102")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "client": "(주)가온아이",
                                  "name": "포털 재구축",
                                  "solution": "검색엔진",
                                  "engagement": "REMOTE",
                                  "contractMm": 2.0,
                                  "startDate": "2026-08-01",
                                  "endDate": "2026-12-31",
                                  "assignments": [
                                    {"personId": 13, "role": "PM", "monthlyMm": 0.5}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONTRACT_PENDING"))
                .andExpect(jsonPath("$.data.phase").value("SALES"))
                .andExpect(jsonPath("$.data.version").value(0));
    }

    @Test
    @DisplayName("생성 — 필수 입력 누락은 400 VALIDATION_ERROR, 필드명을 알려준다")
    void create_missingRequiredField_isValidationError() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .header(CALLER_HEADER, "102")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "client": " ",
                                  "name": "포털 재구축",
                                  "engagement": "REMOTE",
                                  "contractMm": 2.0,
                                  "assignments": [
                                    {"personId": 13, "role": "PM", "monthlyMm": 0.5}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("client"));
        verify(projectCommandService, never()).create(anyLong(), any());
    }

    @Test
    @DisplayName("생성 — 읽을 수 없는 본문은 500이 아니라 400이다")
    void create_unreadableBody_isValidationErrorNotServerError() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .header(CALLER_HEADER, "102")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client\": \"(주)가온아이\", \"contractMm\": \"숫자아님\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("요청 본문을 읽을 수 없습니다"));
        verify(projectCommandService, never()).create(anyLong(), any());
    }

    @Test
    @DisplayName("생성 — 배정 M/M 미지정은 0으로 채운다 (A6-6 기본값과 같은 의미)")
    void create_assignmentWithoutMonthlyMm_defaultsToZero() throws Exception {
        when(projectCommandService.create(anyLong(), any(CreateProjectCommand.class)))
                .thenReturn(detail());
        mockMvc.perform(post("/api/projects")
                        .header(CALLER_HEADER, "102")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "client": "(주)가온아이",
                                  "name": "포털 재구축",
                                  "engagement": "REMOTE",
                                  "contractMm": 2.0,
                                  "assignments": [{"personId": 13, "role": "PM"}]
                                }
                                """))
                .andExpect(status().isCreated());
        ArgumentCaptor<CreateProjectCommand> captor =
                ArgumentCaptor.forClass(CreateProjectCommand.class);
        verify(projectCommandService).create(anyLong(), captor.capture());
        Assertions.assertThat(captor.getValue().assignments().getFirst().monthlyMm()).isZero();
    }

    @Test
    @DisplayName("생성 — 생성 권한 없음은 403 FORBIDDEN 봉투")
    void create_withoutPermission_isForbidden() throws Exception {
        when(projectCommandService.create(anyLong(), any(CreateProjectCommand.class)))
                .thenThrow(new ForbiddenException("프로젝트 생성 권한이 없습니다"));
        mockMvc.perform(post("/api/projects")
                        .header(CALLER_HEADER, "103")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalCreateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("생성 — PM 미지정은 422 PM_REQUIRED 봉투")
    void create_withoutPm_isUnprocessable() throws Exception {
        when(projectCommandService.create(anyLong(), any(CreateProjectCommand.class)))
                .thenThrow(new UnprocessableException(ErrorCode.PM_REQUIRED, "PM을 1명 지정해야 합니다"));
        mockMvc.perform(post("/api/projects")
                        .header(CALLER_HEADER, "102")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalCreateBody()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("PM_REQUIRED"));
    }

    @Test
    @DisplayName("생성 — 중복 이름은 409 DUPLICATE_NAME 봉투")
    void create_duplicateName_isConflict() throws Exception {
        when(projectCommandService.create(anyLong(), any(CreateProjectCommand.class)))
                .thenThrow(new ConflictException(ErrorCode.DUPLICATE_NAME, "같은 이름의 프로젝트가 있습니다"));
        mockMvc.perform(post("/api/projects")
                        .header(CALLER_HEADER, "102")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalCreateBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_NAME"));
    }

    @Test
    @DisplayName("목록 — §7 page 봉투 형태로 나가고 페이징 파라미터가 전달된다")
    void list_returnsSevenSectionPageEnvelope() throws Exception {
        var pageable = PageRequest.of(1, 2);
        when(projectQueryService.listVisible(102L, null, pageable)).thenReturn(new PageImpl<>(
                List.of(new ProjectSummary(
                        PROJECT_ID, "(주)가온아이", "포털 재구축",
                        ProjectStatus.IN_PROGRESS, ProjectPhase.SOLUTION, 90, 13L, "이피엠")),
                pageable,
                5));
        mockMvc.perform(get("/api/projects")
                        .header(CALLER_HEADER, "102")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].managerName").value("이피엠"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3));
    }

    @Test
    @DisplayName("목록 ?phase= — 값이 서비스로 그대로 전달된다")
    void list_passesPhaseFilterThrough() throws Exception {
        var pageable = PageRequest.of(0, 20);
        when(projectQueryService.listVisible(102L, ProjectPhase.SALES, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        mockMvc.perform(get("/api/projects")
                        .header(CALLER_HEADER, "102")
                        .param("phase", "SALES"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("목록 ?phase= — 모르는 값은 §7 봉투 400이다 (조용히 무시하지 않는다)")
    void list_unknownPhase_rejectsWithBadRequest() throws Exception {
        mockMvc.perform(get("/api/projects")
                        .header(CALLER_HEADER, "102")
                        .param("phase", "MAINTENANCE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                // 화면이 어느 칸이 틀렸는지 말해 주려면 field가 필요하다 (§7)
                .andExpect(jsonPath("$.error.field").value("phase"));

        // 오타를 "필터 없음"으로 흘리면 사용자가 전량을 받고 걸렀다고 믿는다
        verify(projectQueryService, never()).listVisible(anyLong(), any(), any());
    }

    @Test
    @DisplayName("단건 — 가시성 밖은 404 은닉 봉투")
    void get_outsideVisibility_isNotFound() throws Exception {
        when(projectQueryService.getProject(103L, PROJECT_ID)).thenThrow(new NotFoundException());
        mockMvc.perform(get("/api/projects/" + PROJECT_ID).header(CALLER_HEADER, "103"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("해당 데이터 없음"));
    }

    @Test
    @DisplayName("단건 — 배정 레코드와 파생 phase를 함께 싣는다")
    void get_visible_returnsDetailWithAssignments() throws Exception {
        when(projectQueryService.getProject(102L, PROJECT_ID)).thenReturn(detail());
        mockMvc.perform(get("/api/projects/" + PROJECT_ID).header(CALLER_HEADER, "102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignments.length()").value(1))
                .andExpect(jsonPath("$.data.assignments[0].personName").value("이피엠"))
                .andExpect(jsonPath("$.data.phase").value("SALES"));
    }

    @Test
    @DisplayName("진척률 — 확인 전 요약 요청은 committed=false로 돌아온다")
    void updateProgress_notConfirmed_returnsSummary() throws Exception {
        when(projectLifecycleService.updateProgress(anyLong(), any(UpdateProgressCommand.class)))
                .thenReturn(new ProgressUpdateResult(
                        PROJECT_ID, "포털 재구축", 90, 95, false, false, 3L));
        mockMvc.perform(put("/api/projects/" + PROJECT_ID + "/progress")
                        .header(CALLER_HEADER, "103")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"progress\": 95, \"version\": 3, \"confirmed\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.committed").value(false))
                .andExpect(jsonPath("$.data.currentProgress").value(90))
                .andExpect(jsonPath("$.data.requestedProgress").value(95));
    }

    @Test
    @DisplayName("진척률 — 경로의 프로젝트 id가 명령에 실린다")
    void updateProgress_usesPathVariableAsTarget() throws Exception {
        when(projectLifecycleService.updateProgress(
                103L, new UpdateProgressCommand(PROJECT_ID, 100, 3L, true)))
                .thenReturn(new ProgressUpdateResult(
                        PROJECT_ID, "포털 재구축", 100, 100, true, true, 4L));
        mockMvc.perform(put("/api/projects/" + PROJECT_ID + "/progress")
                        .header(CALLER_HEADER, "103")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"progress\": 100, \"version\": 3, \"confirmed\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.committed").value(true))
                .andExpect(jsonPath("$.data.completable").value(true))
                .andExpect(jsonPath("$.data.version").value(4));
    }

    @Test
    @DisplayName("진척률 — 범위 밖 값은 경계에서 400, 서비스에 도달하지 않는다")
    void updateProgress_outOfRange_isValidationError() throws Exception {
        mockMvc.perform(put("/api/projects/" + PROJECT_ID + "/progress")
                        .header(CALLER_HEADER, "103")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"progress\": 101, \"version\": 3, \"confirmed\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("progress"));
        verify(projectLifecycleService, never()).updateProgress(anyLong(), any());
    }

    @Test
    @DisplayName("진척률 — version 충돌은 409 STALE_VERSION, 최신 값이 메시지에 담긴다")
    void updateProgress_staleVersion_isConflict() throws Exception {
        when(projectLifecycleService.updateProgress(anyLong(), any(UpdateProgressCommand.class)))
                .thenThrow(new StaleVersionException("최신 진척률 90%, version 3"));
        mockMvc.perform(put("/api/projects/" + PROJECT_ID + "/progress")
                        .header(CALLER_HEADER, "103")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"progress\": 95, \"version\": 1, \"confirmed\": true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STALE_VERSION"))
                .andExpect(jsonPath("$.error.message").value(
                        Matchers.containsString("version 3")));
    }

    @Test
    @DisplayName("수정 — 200 + 경로의 id와 본문이 명령으로 옮겨진다 (A5)")
    void edit_validRequest_passesCommandToService() throws Exception {
        when(projectCommandService.edit(anyLong(), any(EditProjectCommand.class)))
                .thenReturn(detail());
        mockMvc.perform(put("/api/projects/" + PROJECT_ID)
                        .header(CALLER_HEADER, "104")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editBody("ORDER_CONFIRMED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(PROJECT_ID));
        ArgumentCaptor<EditProjectCommand> captor =
                ArgumentCaptor.forClass(EditProjectCommand.class);
        verify(projectCommandService).edit(Mockito.eq(104L), captor.capture());
        Assertions.assertThat(captor.getValue().projectId()).isEqualTo(PROJECT_ID);
        Assertions.assertThat(captor.getValue().status())
                .isEqualTo(ProjectStatus.ORDER_CONFIRMED);
        Assertions.assertThat(captor.getValue().version()).isEqualTo(3L);
    }

    @Test
    @DisplayName("수정 — 상태 누락은 경계에서 400, 서비스에 도달하지 않는다")
    void edit_withoutStatus_isValidationError() throws Exception {
        mockMvc.perform(put("/api/projects/" + PROJECT_ID)
                        .header(CALLER_HEADER, "104")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "client": "(주)가온아이",
                                  "name": "포털 재구축",
                                  "engagement": "REMOTE",
                                  "contractMm": 2.0,
                                  "version": 3
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("status"));
        verify(projectCommandService, never()).edit(anyLong(), any());
    }

    @Test
    @DisplayName("수정 — 전이 위반은 409 INVALID_TRANSITION 봉투")
    void edit_invalidTransition_isConflict() throws Exception {
        when(projectCommandService.edit(anyLong(), any(EditProjectCommand.class)))
                .thenThrow(new ConflictException(ErrorCode.INVALID_TRANSITION, "진행중에서 계약대기로는 바꿀 수 없습니다"));
        mockMvc.perform(put("/api/projects/" + PROJECT_ID)
                        .header(CALLER_HEADER, "104")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editBody("CONTRACT_PENDING")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_TRANSITION"));
    }

    @Test
    @DisplayName("수정 — 참여자의 정보 수정은 403 FORBIDDEN 봉투 (A5-3)")
    void edit_participant_isForbidden() throws Exception {
        when(projectCommandService.edit(anyLong(), any(EditProjectCommand.class)))
                .thenThrow(new ForbiddenException("담당자만 가능"));
        mockMvc.perform(put("/api/projects/" + PROJECT_ID)
                        .header(CALLER_HEADER, "105")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editBody("IN_PROGRESS")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("완료 처리 — 200 + version이 서비스로 전달된다 (A7-1)")
    void complete_validRequest_passesVersion() throws Exception {
        when(projectLifecycleService.complete(103L, PROJECT_ID, 3L)).thenReturn(detail());
        mockMvc.perform(post("/api/projects/" + PROJECT_ID + "/complete")
                        .header(CALLER_HEADER, "103")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(PROJECT_ID));
        verify(projectLifecycleService).complete(103L, PROJECT_ID, 3L);
    }

    @Test
    @DisplayName("완료 처리 — 진척률 미달은 409 PROGRESS_INCOMPLETE 봉투 (A7-2)")
    void complete_progressIncomplete_isConflict() throws Exception {
        when(projectLifecycleService.complete(anyLong(), anyLong(), anyLong()))
                .thenThrow(new ConflictException(ErrorCode.PROGRESS_INCOMPLETE,
                        "진척률 100%에서만 완료 처리할 수 있습니다 (현재 90%)"));
        mockMvc.perform(post("/api/projects/" + PROJECT_ID + "/complete")
                        .header(CALLER_HEADER, "103")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROGRESS_INCOMPLETE"));
    }

    @Test
    @DisplayName("완료 처리 — version 누락은 경계에서 400")
    void complete_withoutVersion_isValidationError() throws Exception {
        mockMvc.perform(post("/api/projects/" + PROJECT_ID + "/complete")
                        .header(CALLER_HEADER, "103")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verify(projectLifecycleService, never()).complete(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("이관 — 201 + 계약 정보와 사이트가 spec으로 전달된다 (D1-1)")
    void handover_validRequest_isCreatedAndPassesSpec() throws Exception {
        when(projectLifecycleService.handover(anyLong(), anyLong(), any(), anyLong()))
                .thenReturn(detail());

        mockMvc.perform(post("/api/projects/" + PROJECT_ID + "/handover")
                        .header(CALLER_HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractor": "명화공업", "name": "MES 유지보수",
                                 "startDate": "2026-09-01", "endDate": "2027-08-31",
                                 "amount": 24000000, "monthlyAmount": 2000000,
                                 "sites": [{"name": "명화공업 본사", "engineerId": 26}],
                                 "version": 3}"""))
                // 생성이므로 201이다 (AC D1-1) — 계약과 사이트가 새로 생긴다
                .andExpect(status().isCreated());

        ArgumentCaptor<HandoverSpec> spec = ArgumentCaptor.forClass(HandoverSpec.class);
        verify(projectLifecycleService)
                .handover(eq(13L), eq(PROJECT_ID), spec.capture(), eq(3L));
        assertThat(spec.getValue().contractor()).isEqualTo("명화공업");
        assertThat(spec.getValue().name()).isEqualTo("MES 유지보수");
        assertThat(spec.getValue().amount()).isEqualTo(24000000L);
        assertThat(spec.getValue().sites()).singleElement().satisfies(site -> {
            assertThat(site.name()).isEqualTo("명화공업 본사");
            assertThat(site.engineerId()).isEqualTo(26L);
        });
    }

    @Test
    @DisplayName("이관 — 사이트가 빈 배열이면 경계에서 400 (D1-1 사이트 1개 이상)")
    void handover_withoutSites_isValidationError() throws Exception {
        mockMvc.perform(post("/api/projects/" + PROJECT_ID + "/handover")
                        .header(CALLER_HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractor": "명화공업", "name": "MES 유지보수",
                                 "sites": [], "version": 3}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verify(projectLifecycleService, never())
                .handover(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("이관 — 사이트 안의 담당 엔지니어 누락도 경계에서 400 (중첩 @Valid)")
    void handover_siteWithoutEngineer_isValidationError() throws Exception {
        // 목록에 @Valid가 없으면 요소의 애너테이션이 조용히 무시되고 서버 안까지 간다
        mockMvc.perform(post("/api/projects/" + PROJECT_ID + "/handover")
                        .header(CALLER_HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractor": "명화공업", "name": "MES 유지보수",
                                 "sites": [{"name": "명화공업 본사"}], "version": 3}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verify(projectLifecycleService, never())
                .handover(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("이관 — version 누락은 경계에서 400")
    void handover_withoutVersion_isValidationError() throws Exception {
        mockMvc.perform(post("/api/projects/" + PROJECT_ID + "/handover")
                        .header(CALLER_HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractor": "명화공업", "name": "MES 유지보수",
                                 "sites": [{"name": "명화공업 본사", "engineerId": 26}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verify(projectLifecycleService, never())
                .handover(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("이관 — 완료가 아니면 409 INVALID_TRANSITION 봉투 (D1-2)")
    void handover_notCompleted_isConflict() throws Exception {
        when(projectLifecycleService.handover(anyLong(), anyLong(), any(), anyLong()))
                .thenThrow(new ConflictException(ErrorCode.INVALID_TRANSITION,
                        "완료된 프로젝트만 유지보수로 이관할 수 있습니다 (현재 진행중)"));

        mockMvc.perform(post("/api/projects/" + PROJECT_ID + "/handover")
                        .header(CALLER_HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractor": "명화공업", "name": "MES 유지보수",
                                 "sites": [{"name": "명화공업 본사", "engineerId": 26}],
                                 "version": 3}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_TRANSITION"));
    }

    @Test
    @DisplayName("재개 — 200, 전용 경로로만 들어온다 (A7-3)")
    void reopen_validRequest_delegatesToService() throws Exception {
        when(projectLifecycleService.reopen(103L, PROJECT_ID, 5L)).thenReturn(detail());
        mockMvc.perform(post("/api/projects/" + PROJECT_ID + "/reopen")
                        .header(CALLER_HEADER, "103")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 5}"))
                .andExpect(status().isOk());
        verify(projectLifecycleService).reopen(103L, PROJECT_ID, 5L);
    }

    @Test
    @DisplayName("PM 교체 — 200 + 대상 인원과 version이 서비스로 전달된다 (A6-1)")
    void changeManager_passesPersonAndVersion() throws Exception {
        when(projectLifecycleService.changeManager(13L, PROJECT_ID, 105L, 3L)).thenReturn(detail());
        mockMvc.perform(put("/api/projects/" + PROJECT_ID + "/pm")
                        .header(CALLER_HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personId\": 105, \"version\": 3}"))
                .andExpect(status().isOk());
        verify(projectLifecycleService).changeManager(13L, PROJECT_ID, 105L, 3L);
    }

    @Test
    @DisplayName("PM 교체 — 이미 PM인 사람을 다시 지정하면 422 INVALID_ROLE 봉투")
    void changeManager_samePerson_isUnprocessable() throws Exception {
        when(projectLifecycleService.changeManager(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenThrow(new UnprocessableException(ErrorCode.INVALID_ROLE, "이미 이 프로젝트의 PM입니다"));
        mockMvc.perform(put("/api/projects/" + PROJECT_ID + "/pm")
                        .header(CALLER_HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personId\": 13, \"version\": 3}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("INVALID_ROLE"));
    }

    @Test
    @DisplayName("역할 지정 — 200 + 대상 인원·역할이 서비스로 전달된다 (A6-3)")
    void changeRole_passesPersonAndRole() throws Exception {
        when(projectLifecycleService.changeRole(13L, PROJECT_ID, 105L, ProjectRole.PL))
                .thenReturn(detail());
        mockMvc.perform(put("/api/projects/" + PROJECT_ID + "/roles")
                        .header(CALLER_HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personId\": 105, \"role\": \"PL\"}"))
                .andExpect(status().isOk());
        verify(projectLifecycleService).changeRole(13L, PROJECT_ID, 105L, ProjectRole.PL);
    }

    @Test
    @DisplayName("역할 지정 — role=PM은 422 INVALID_ROLE 봉투 (A6-7)")
    void changeRole_toPm_isUnprocessable() throws Exception {
        when(projectLifecycleService.changeRole(anyLong(), anyLong(), anyLong(), any()))
                .thenThrow(new UnprocessableException(ErrorCode.INVALID_ROLE,
                        "PM은 이 경로로 지정할 수 없습니다 — PM 교체를 쓰세요"));
        mockMvc.perform(put("/api/projects/" + PROJECT_ID + "/roles")
                        .header(CALLER_HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personId\": 105, \"role\": \"PM\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("INVALID_ROLE"));
    }

    @Test
    @DisplayName("삭제 — 200 + success:true, 본문 없이 경로 id만으로 처리한다 (A4-1)")
    void delete_returnsNoContent() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/projects/" + PROJECT_ID)
                        .header(CALLER_HEADER, "13"))
                .andExpect(status().isOk());
        verify(projectCommandService).delete(13L, PROJECT_ID);
    }

    @Test
    @DisplayName("삭제 — 권한 없음은 403 FORBIDDEN 봉투 (A4-2)")
    void delete_withoutPermission_isForbidden() throws Exception {
        Mockito.doThrow(new ForbiddenException("담당자만 가능"))
                .when(projectCommandService).delete(anyLong(), anyLong());
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/projects/" + PROJECT_ID)
                        .header(CALLER_HEADER, "105"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
    private String editBody(String status) {
        return """
                {
                  "client": "(주)가온아이",
                  "name": "포털 재구축",
                  "solution": "검색엔진",
                  "engagement": "REMOTE",
                  "contractMm": 2.0,
                  "status": "%s",
                  "version": 3
                }
                """.formatted(status);
    }
    private String minimalCreateBody() {
        return """
                {
                  "client": "(주)가온아이",
                  "name": "포털 재구축",
                  "engagement": "REMOTE",
                  "contractMm": 2.0,
                  "assignments": [
                    {"personId": 13, "role": "PM", "monthlyMm": 0.5}
                  ]
                }
                """;
    }
    private ProjectDetail detail() {
        return new ProjectDetail(
                PROJECT_ID,
                "(주)가온아이",
                "포털 재구축",
                "검색엔진",
                Engagement.REMOTE,
                ProjectStatus.CONTRACT_PENDING,
                ProjectPhase.SALES,
                0,
                2.0,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31),
                13L,
                0L,
                List.of(new AssignmentView(
                        31L, 13L, "이피엠", true, ProjectRole.PM,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), 0.5, 0L)));
    }
}
