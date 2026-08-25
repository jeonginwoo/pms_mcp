package kr.proten.pms.person.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.proten.pms.person.service.dto.UpdateProfileCommand;

/**
 * 내 프로필 수정 요청 (AC H1-2).
 *
 * <p>{@code phone}에 제약이 형식이 아니라 길이뿐인 이유: 시드 연락처가 하이픈·괄호·
 * 내선 표기가 섞인 실데이터라(부록 B) 형식을 걸면 실제로 쓰이는 값을 거부한다.
 * 소속·직급·권한 그룹 칸이 없는 것도 의도다 — 그것은 관리자 경로(E2-2)의 몫이고,
 * 여기에 열면 자기 권한 그룹을 스스로 바꿀 수 있게 된다.
 */
public record UpdateProfileRequest(
        @NotBlank(message = "이름은 필수입니다")
        @Size(max = 50, message = "이름은 50자를 넘을 수 없습니다") String name,
        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "이메일 형식이 아닙니다")
        @Size(max = 200, message = "이메일은 200자를 넘을 수 없습니다") String email,
        @Size(max = 50, message = "전화번호는 50자를 넘을 수 없습니다") String phone) {

    public UpdateProfileCommand toCommand() {
        return new UpdateProfileCommand(name, email, phone);
    }
}
