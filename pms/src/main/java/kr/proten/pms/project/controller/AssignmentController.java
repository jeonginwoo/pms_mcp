package kr.proten.pms.project.controller;

import jakarta.validation.Valid;
import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.common.web.ApiResponse;
import kr.proten.pms.project.controller.dto.*;
import kr.proten.pms.project.service.AssignmentService;
import kr.proten.pms.project.service.dto.AssignmentView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인력 배정 API — EPIC B (§7 라우트: `POST /projects/{id}/assignments` ·
 * `PUT`·`DELETE /assignments/{id}`).
 *
 * 생성만 프로젝트 아래에 있고 수정·종료는 배정 id로 들어온다 — §7이 정한 형태이며,
 * 어느 쪽이든 서비스가 먼저 프로젝트 가시성을 통과시킨다(404 은닉).
 * 목록은 별도 라우트를 만들지 않았다: 프로젝트 상세(A3-3)가 배정을 이미 싣는다.
 */
@RestController
@RequestMapping("/api")
class AssignmentController {
    private final AssignmentService assignmentService;

    AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /** 인력 배정 (AC B1-1) — 성공 시 201 + 배정 항목. */
    @PostMapping("/projects/{projectId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<AssignmentView> assign(
            @CallerPersonId long callerPersonId,
            @PathVariable long projectId,
            @Valid @RequestBody CreateAssignmentRequest request) {
        return ApiResponse.ok(
                assignmentService.assign(callerPersonId, request.toCommand(projectId)));
    }

    /** 배정 수정 (AC B1-4) — 기간·투입 M/M. */
    @PutMapping("/assignments/{assignmentId}")
    ApiResponse<AssignmentView> update(
            @CallerPersonId long callerPersonId,
            @PathVariable long assignmentId,
            @Valid @RequestBody UpdateAssignmentRequest request) {
        return ApiResponse.ok(
                assignmentService.update(callerPersonId, request.toCommand(assignmentId)));
    }

    /** 배정 종료 (AC B2-1) — 행은 남고 상태가 종료로 바뀐다. */
    @DeleteMapping("/assignments/{assignmentId}")
    ApiResponse<Void> close(
            @CallerPersonId long callerPersonId,
            @PathVariable long assignmentId) {
        assignmentService.close(callerPersonId, assignmentId);

        return ApiResponse.ok();
    }
}
