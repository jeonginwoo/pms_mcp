package kr.proten.pms.project.controller;

import jakarta.validation.Valid;
import kr.proten.pms.audit.AuditRecord;
import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.common.web.ApiResponse;
import kr.proten.pms.common.web.PageResponse;
import kr.proten.pms.project.controller.dto.ChangeManagerRequest;
import kr.proten.pms.project.controller.dto.ChangeRoleRequest;
import kr.proten.pms.project.controller.dto.CreateProjectRequest;
import kr.proten.pms.project.controller.dto.EditProjectRequest;
import kr.proten.pms.project.controller.dto.UpdateProgressRequest;
import kr.proten.pms.project.controller.dto.VersionRequest;
import kr.proten.pms.project.service.ProjectCommandService;
import kr.proten.pms.project.service.ProjectLifecycleService;
import kr.proten.pms.project.service.ProjectQueryService;
import kr.proten.pms.project.service.dto.ProgressUpdateResult;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.dto.ProjectSummary;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프로젝트 API — EPIC A의 구현 범위(A1 생성 · A2 진척률 2단계 · A3 가시성 조회 ·
 * A4 소프트 삭제 · A5 정보·상태 수정 · A6-1 PM 교체 · A7 완료 처리·재개) + G2-2 이력.
 *
 * 컨트롤러는 HTTP 변환만 한다 — 가시성·권한·404 은닉·낙관적 락은 전부 서비스가
 * 판정하고, 예외 → 에러 봉투 변환은 common의 전역 핸들러 한 곳이 담당한다.
 * 서비스는 셋이다: 조회 · CRUD · 생애주기(§5 상태 머신).
 * A6-3(역할 지정)·A8(권한 커스텀)은 아직 라우트를 만들지 않는다.
 */
@RestController
@RequestMapping("/api/projects")
class ProjectController {
    private final ProjectQueryService projectQueryService;
    private final ProjectCommandService projectCommandService;
    private final ProjectLifecycleService projectLifecycleService;

    ProjectController(
            ProjectQueryService projectQueryService,
            ProjectCommandService projectCommandService,
            ProjectLifecycleService projectLifecycleService) {
        this.projectQueryService = projectQueryService;
        this.projectCommandService = projectCommandService;
        this.projectLifecycleService = projectLifecycleService;
    }

    /** 프로젝트 생성 (AC A1-1) — 성공 시 201 + 생성된 상세. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<ProjectDetail> create(
            @CallerPersonId long callerPersonId,
            @Valid @RequestBody CreateProjectRequest request) {
        return ApiResponse.ok(projectCommandService.create(callerPersonId, request.toCommand()));
    }

    /**
     * 가시성 범위 내 프로젝트 목록 (AC A3-1) — page 봉투.
     * 페이징·정렬은 ?page=0&size=20&sort=field,desc (§7).
     * ASSUMPTION: ?phase= 파생 필터(§7·v2.4)는 아직 없다 — 목록 화면 작업과 함께 넣는다.
     */
    @GetMapping
    ApiResponse<PageResponse<ProjectSummary>> list(
            @CallerPersonId long callerPersonId, Pageable pageable) {
        return ApiResponse.ok(
                PageResponse.of(projectQueryService.listVisible(callerPersonId, pageable)));
    }

    /** 프로젝트 단건 조회 (AC A3-2·A3-3) — 가시성 밖은 404 은닉. */
    @GetMapping("/{projectId}")
    ApiResponse<ProjectDetail> get(@CallerPersonId long callerPersonId, @PathVariable long projectId) {
        return ApiResponse.ok(projectQueryService.getProject(callerPersonId, projectId));
    }

    /**
     * 프로젝트별 변경 이력 (AC G2-2) — 가시성 범위이면 역할과 무관하게 볼 수 있다.
     * 가시성 밖은 403이 아니라 404다(G2-3 — 상세 조회와 같은 은닉 의미론).
     */
    @GetMapping("/{projectId}/audit")
    ApiResponse<PageResponse<AuditRecord>> audit(
            @CallerPersonId long callerPersonId,
            @PathVariable long projectId,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(
                projectQueryService.listAudit(callerPersonId, projectId, pageable)));
    }

    /** 정보·상태 수정 (AC A5-1~A5-3) — 완료·재개·이관은 이 경로가 아니다. */
    @PutMapping("/{projectId}")
    ApiResponse<ProjectDetail> edit(
            @CallerPersonId long callerPersonId,
            @PathVariable long projectId,
            @Valid @RequestBody EditProjectRequest request) {
        return ApiResponse.ok(
                projectCommandService.edit(callerPersonId, request.toCommand(projectId)));
    }

    /** 진척률 2단계 갱신 (AC A2-1·A2-2) — confirmed=false 요약 → true 커밋. */
    @PutMapping("/{projectId}/progress")
    ApiResponse<ProgressUpdateResult> updateProgress(
            @CallerPersonId long callerPersonId,
            @PathVariable long projectId,
            @Valid @RequestBody UpdateProgressRequest request) {
        return ApiResponse.ok(
                projectLifecycleService.updateProgress(callerPersonId, request.toCommand(projectId)));
    }

    /** 완료 처리 (AC A7-1) — 진행중·진척률 100%가 전제다. */
    @PostMapping("/{projectId}/complete")
    ApiResponse<ProjectDetail> complete(
            @CallerPersonId long callerPersonId,
            @PathVariable long projectId,
            @Valid @RequestBody VersionRequest request) {
        return ApiResponse.ok(
                projectLifecycleService.complete(callerPersonId, projectId, request.version()));
    }

    /** 재개 (AC A7-3) — 완료 → 진행중, 진척률은 90으로 돌아간다. */
    @PostMapping("/{projectId}/reopen")
    ApiResponse<ProjectDetail> reopen(
            @CallerPersonId long callerPersonId,
            @PathVariable long projectId,
            @Valid @RequestBody VersionRequest request) {
        return ApiResponse.ok(
                projectLifecycleService.reopen(callerPersonId, projectId, request.version()));
    }

    /** PM 교체 (AC A6-1) — 대상이 미배정이면 배정을 함께 만든다. */
    @PutMapping("/{projectId}/pm")
    ApiResponse<ProjectDetail> changeManager(
            @CallerPersonId long callerPersonId,
            @PathVariable long projectId,
            @Valid @RequestBody ChangeManagerRequest request) {
        return ApiResponse.ok(projectLifecycleService.changeManager(callerPersonId, projectId,
                request.personId(), request.version()));
    }

    /**
     * 역할 지정·교체 (AC A6-3) — PL·참여자만. 미배정 대상이면 배정을 함께 만들고(A6-6),
     * `role=PM`은 422다(A6-7 — PM 교체는 `/pm` 전용).
     */
    @PutMapping("/{projectId}/roles")
    ApiResponse<ProjectDetail> changeRole(
            @CallerPersonId long callerPersonId,
            @PathVariable long projectId,
            @Valid @RequestBody ChangeRoleRequest request) {
        return ApiResponse.ok(projectLifecycleService.changeRole(callerPersonId, projectId,
                request.personId(), request.role()));
    }

    /** 소프트 삭제 (AC A4-1) — PM 또는 프로젝트 생성 권한자만 (2026-08-22 결정). */
    @DeleteMapping("/{projectId}")
    ApiResponse<Void> delete(@CallerPersonId long callerPersonId, @PathVariable long projectId) {
        projectCommandService.delete(callerPersonId, projectId);

        return ApiResponse.ok();
    }
}
