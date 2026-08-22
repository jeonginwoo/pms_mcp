package kr.proten.pms.person.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import kr.proten.pms.person.service.dto.GradeCommand;

/**
 * 직급 등록·수정 요청 (AC E4-1·E4-2).
 * coeff가 양수여야 하는 이유: 보정 가동률의 가중치라 0이면 그 직급의 부하가 사라진다.
 */
public record GradeRequest(
        @NotBlank(message = "직급명은 필수입니다") String name,
        @Positive(message = "계수는 0보다 커야 합니다") double coeff,
        long version) {

    public GradeCommand toCommand(Long gradeId) {
        return new GradeCommand(gradeId, name, coeff, version);
    }
}
