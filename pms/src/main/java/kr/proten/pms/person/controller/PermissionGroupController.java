package kr.proten.pms.person.controller;

import jakarta.validation.Valid;
import java.util.List;
import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.common.web.ApiResponse;
import kr.proten.pms.person.controller.dto.*;
import kr.proten.pms.person.service.PermissionGroupService;
import kr.proten.pms.person.service.dto.PermissionGroupDetail;
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
 * 권한 그룹 API (PRD-pms §7 `/api/permission-groups`) — US-E5.
 *
 * 사용자별 그룹 부여는 여기 없다 — 그룹은 사람의 속성이라 `PUT /api/people/{id}`가
 * 담당한다(2026-08-09 ⑦). 같은 사실을 두 경로로 바꾸게 두지 않는다.
 */
@RestController
@RequestMapping("/api/permission-groups")
class PermissionGroupController {
    private final PermissionGroupService permissionGroupService;

    PermissionGroupController(PermissionGroupService permissionGroupService) {
        this.permissionGroupService = permissionGroupService;
    }

    @GetMapping
    ApiResponse<List<ReferenceItem>> list(@CallerPersonId long callerPersonId) {
        return ApiResponse.ok(permissionGroupService.list(callerPersonId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<PermissionGroupDetail> create(
            @CallerPersonId long callerPersonId,
            @Valid @RequestBody PermissionGroupRequest request) {
        return ApiResponse.ok(
                permissionGroupService.create(callerPersonId, request.toCommand(null)));
    }

    /** 관리자 그룹은 422 IMMUTABLE_GROUP — 자기 잠금 방지 (AC E5-3). */
    @PutMapping("/{groupId}")
    ApiResponse<PermissionGroupDetail> update(
            @CallerPersonId long callerPersonId,
            @PathVariable long groupId,
            @Valid @RequestBody PermissionGroupRequest request) {
        return ApiResponse.ok(
                permissionGroupService.update(callerPersonId, request.toCommand(groupId)));
    }

    /** 소속 인원이 있으면 409 IN_USE (AC E5-4). */
    @DeleteMapping("/{groupId}")
    ApiResponse<Void> delete(@CallerPersonId long callerPersonId, @PathVariable long groupId) {
        permissionGroupService.delete(callerPersonId, groupId);

        return ApiResponse.ok();
    }
}
