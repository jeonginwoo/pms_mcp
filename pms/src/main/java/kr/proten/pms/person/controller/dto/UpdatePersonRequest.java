package kr.proten.pms.person.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kr.proten.pms.person.service.dto.UpdatePersonCommand;

/**
 * 인력 수정 요청 (AC E2-2) — 이름·소속·직급·권한 그룹.
 * email이 없는 이유는 로그인 ID 변경이 본인 경로(H1-2)이기 때문이다.
 */
public record UpdatePersonRequest(
        @NotBlank(message = "이름은 필수입니다") String name,
        @NotNull(message = "소속 조직은 필수입니다") Long orgUnitId,
        @NotNull(message = "직급은 필수입니다") Long gradeId,
        @NotNull(message = "권한 그룹은 필수입니다") Long groupId,
        @NotNull(message = "집계 대상 여부는 필수입니다") Boolean billable,
        long version) {

    public UpdatePersonCommand toCommand(long personId) {
        return new UpdatePersonCommand(personId, name, orgUnitId, gradeId, groupId, billable, version);
    }
}
