package kr.proten.pms.person.controller.dto;

import jakarta.validation.constraints.NotBlank;
import kr.proten.pms.person.service.dto.PermissionGroupCommand;

/**
 * 권한 그룹 등록·수정 요청 (AC E5-1·E5-2).
 *
 * `visibilityScope`를 문자열로 받고 값 검증을 서비스에 맡기는 이유: 모르는 열거 값은
 * 형식 오류(400)가 아니라 의미 오류(422)라서(§7 에러 표) 어노테이션으로 거르면
 * 상태 코드가 계약과 어긋난다.
 */
public record PermissionGroupRequest(
        @NotBlank(message = "그룹명은 필수입니다") String name,
        @NotBlank(message = "가시성 범위는 필수입니다") String visibilityScope,
        boolean createProject,
        boolean manageContracts,
        boolean manageAllProjects,
        boolean manageOrg,
        long version) {

    public PermissionGroupCommand toCommand(Long groupId) {
        return new PermissionGroupCommand(groupId, name, visibilityScope,
                createProject, manageContracts, manageAllProjects, manageOrg, version);
    }
}
