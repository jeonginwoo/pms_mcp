package kr.proten.pms.project.controller;

import jakarta.validation.Valid;
import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.project.service.ProgressUpdateService;
import kr.proten.pms.project.service.ProjectCommandService;
import kr.proten.pms.project.service.ProjectCompletionService;
import kr.proten.pms.project.service.ProjectDeleteService;
import kr.proten.pms.project.service.ProjectEditService;
import kr.proten.pms.project.service.ProjectQueryService;
import kr.proten.pms.project.service.ProjectRoleService;
import kr.proten.pms.project.service.dto.ProgressUpdateResult;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.dto.ProjectSummary;
import org.springframework.data.domain.Page;
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
 * A4 소프트 삭제 · A5 정보·상태 수정 · A6-1 PM 교체 · A7 완료 처리·재개).
 *
 * 컨트롤러는 HTTP 변환만 한다 — 가시성·권한·404 은닉·낙관적 락은 전부 서비스가
 * 판정하고, 예외 → 에러 봉투 변환은 common의 전역 핸들러 한 곳이 담당한다.
 * A6-3(역할 지정)·A8(권한 커스텀)은 아직 라우트를 만들지 않는다.
 */
@RestController
@RequestMapping("/api/projects")
class ProjectController {
    private final ProjectCommandService projectCommandService;
    private final ProjectEditService projectEditService;
    private final ProjectCompletionService projectCompletionService;
    private final ProjectRoleService projectRoleService;
    private final ProjectDeleteService projectDeleteService;
    private final ProjectQueryService projectQueryService;
    private final ProgressUpdateService progressUpdateService;

    ProjectController(
            ProjectCommandService projectCommandService,
            ProjectEditService projectEditService,
            ProjectCompletionService projectCompletionService,
            ProjectRoleService projectRoleService,
            ProjectDeleteService projectDeleteService,
            ProjectQueryService projectQueryService,
            ProgressUpdateService progressUpdateService) {
        this.projectCommandService = projectCommandService;
        this.projectEditService = projectEditService;
        this.projectCompletionService = projectCompletionService;
        this.projectRoleService = projectRoleService;
        this.projectDeleteService = projectDeleteService;
        this.projectQueryService = projectQueryService;
        this.progressUpdateService = progressUpdateService;
    }

    /** 프로젝트 생성 (AC A1-1) — 성공 시 201 + 생성된 상세. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProjectDetail create(
            @CallerPersonId long callerPersonId,
            @Valid @RequestBody CreateProjectRequest request) {
        return projectCommandService.create(callerPersonId, request.toCommand());
    }

    /**
     * 가시성 범위 내 프로젝트 목록 (AC A3-1) — page 봉투.
     * 페이징·정렬은 ?page=0&size=20&sort=field,desc (§7).
     * ASSUMPTION: ?phase= 파생 필터(§7·v2.4)는 아직 없다 — 목록 화면 작업과 함께 넣는다.
     */
    @GetMapping
    PageResponse<ProjectSummary> list(@CallerPersonId long callerPersonId, Pageable pageable) {
        Page<ProjectSummary> page = projectQueryService.listVisible(callerPersonId, pageable);

        return PageResponse.of(page);
    }

    /** 프로젝트 단건 조회 (AC A3-2·A3-3) — 가시성 밖은 404 은닉. */
    @GetMapping("/{projectId}")
    ProjectDetail get(@CallerPersonId long callerPersonId, @PathVariable long projectId) {
        return projectQueryService.getProject(callerPersonId, projectId);
    }

    /** 정보·상태 수정 (AC A5-1~A5-3) — 완료·재개·이관은 이 경로가 아니다. */
    @PutMapping("/{projectId}")
    ProjectDetail edit(
            @CallerPersonId long callerPersonId,
            @PathVariable long projectId,
            @Valid @RequestBody EditProjectRequest request) {
        return projectEditService.edit(callerPersonId, request.toCommand(projectId));
    }

    /** 진척률 2단계 갱신 (AC A2-1·A2-2) — confirmed=false 요약 → true 커밋. */
    @PutMapping("/{projectId}/progress")
    ProgressUpdateResult updateProgress(
            @CallerPersonId long callerPersonId,
            @PathVariable long projectId,
            @Valid @RequestBody UpdateProgressRequest request) {
        return progressUpdateService.update(callerPersonId, request.toCommand(projectId));
    }

    /** 완료 처리 (AC A7-1) — 진행중·진척률 100%가 전제다. */
    @PostMapping("/{projectId}/complete")
    ProjectDetail complete(
            @CallerPersonId long callerPersonId,
            @PathVariable long projectId,
            @Valid @RequestBody VersionRequest request) {
        return projectCompletionService.complete(callerPersonId, projectId, request.version());
    }

    /** 재개 (AC A7-3) — 완료 → 진행중, 진척률은 90으로 돌아간다. */
    @PostMapping("/{projectId}/reopen")
    ProjectDetail reopen(
            @CallerPersonId long callerPersonId,
            @PathVariable long projectId,
            @Valid @RequestBody VersionRequest request) {
        return projectCompletionService.reopen(callerPersonId, projectId, request.version());
    }

    /** PM 교체 (AC A6-1) — 대상이 미배정이면 배정을 함께 만든다. */
    @PutMapping("/{projectId}/pm")
    ProjectDetail changeManager(
            @CallerPersonId long callerPersonId,
            @PathVariable long projectId,
            @Valid @RequestBody ChangeManagerRequest request) {
        return projectRoleService.changeManager(callerPersonId, projectId, request.personId(),
                request.version());
    }

    /** 소프트 삭제 (AC A4-1) — PM 또는 프로젝트 생성 권한자만 (2026-08-22 결정). */
    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CallerPersonId long callerPersonId, @PathVariable long projectId) {
        projectDeleteService.delete(callerPersonId, projectId);
    }
}
