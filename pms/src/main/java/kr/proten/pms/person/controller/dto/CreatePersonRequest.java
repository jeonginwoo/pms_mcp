package kr.proten.pms.person.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kr.proten.pms.person.service.dto.CreatePersonCommand;

/**
 * 인력 등록 요청 (AC E2-1).
 * email은 로그인 ID다 — 초기 비밀번호는 부록 B 확정값이라 본문에 없다.
 */
public record CreatePersonRequest(
        @NotBlank(message = "이름은 필수입니다") String name,
        @NotNull(message = "소속 조직은 필수입니다") Long orgUnitId,
        @NotNull(message = "직급은 필수입니다") Long gradeId,
        @NotNull(message = "권한 그룹은 필수입니다") Long groupId,
        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "이메일 형식이 아닙니다") String email) {

    public CreatePersonCommand toCommand() {
        return new CreatePersonCommand(name, orgUnitId, gradeId, groupId, email);
    }
}
