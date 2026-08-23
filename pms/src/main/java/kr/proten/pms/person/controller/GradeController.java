package kr.proten.pms.person.controller;

import jakarta.validation.Valid;
import java.util.List;
import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.common.web.ApiResponse;
import kr.proten.pms.person.controller.dto.GradeRequest;
import kr.proten.pms.person.service.GradeService;
import kr.proten.pms.person.service.dto.GradeDetail;
import kr.proten.pms.person.service.dto.ReferenceItem;
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
 * 직급 API (PRD-pms §7 `/api/grades`) — US-E4.
 *
 * 조회는 등록 폼의 선택 목록(`ReferenceItem` 목록), 쓰기는 관리 화면(`GradeService`)이라
 * 서비스가 둘이다 — 응답 형태도 다르다(id·이름 vs 계수 포함).
 */
@RestController
@RequestMapping("/api/grades")
class GradeController {
    private final GradeService gradeService;

    GradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    @GetMapping
    ApiResponse<List<ReferenceItem>> list(@CallerPersonId long callerPersonId) {
        return ApiResponse.ok(gradeService.list(callerPersonId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<GradeDetail> create(
            @CallerPersonId long callerPersonId,
            @Valid @RequestBody GradeRequest request) {
        return ApiResponse.ok(gradeService.create(callerPersonId, request.toCommand(null)));
    }

    /** 계수 변경은 캐시가 없어 다음 가동률 조회부터 반영된다 (AC E4-2). */
    @PutMapping("/{gradeId}")
    ApiResponse<GradeDetail> update(
            @CallerPersonId long callerPersonId,
            @PathVariable long gradeId,
            @Valid @RequestBody GradeRequest request) {
        return ApiResponse.ok(gradeService.update(callerPersonId, request.toCommand(gradeId)));
    }

    /** 쓰는 인원이 있으면 409 IN_USE (AC E4-3). */
    @DeleteMapping("/{gradeId}")
    ApiResponse<Void> delete(@CallerPersonId long callerPersonId, @PathVariable long gradeId) {
        gradeService.delete(callerPersonId, gradeId);

        return ApiResponse.ok();
    }
}
