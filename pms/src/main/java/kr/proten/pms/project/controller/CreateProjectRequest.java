package kr.proten.pms.project.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.List;
import kr.proten.pms.project.service.dto.CreateProjectCommand;
import kr.proten.pms.project.service.entity.Engagement;

/**
 * 프로젝트 생성 요청 (AC A1-1·A1-4).
 * 상태·진척률은 규칙으로 정해지므로(계약대기·0) 입력에 없다.
 *
 * 형식 검증은 여기서 선언적으로 끝낸다(conventions §4 — 400 VALIDATION_ERROR).
 * PM 1행 불변식처럼 애노테이션으로 표현할 수 없는 규칙은 서비스가 422로 판정한다.
 */
public record CreateProjectRequest(
        @NotBlank(message = "고객사는 필수입니다") String client,
        @NotBlank(message = "프로젝트명은 필수입니다") String name,
        String solution,
        @NotNull(message = "수행 형태는 필수입니다") Engagement engagement,
        @NotNull(message = "계약 M/M은 필수입니다")
        @PositiveOrZero(message = "계약 M/M은 0 이상이어야 합니다") Double contractMm,
        LocalDate startDate,
        LocalDate endDate,
        @NotEmpty(message = "참여자를 1명 이상 지정해야 합니다")
        @Valid List<AssignmentRequest> assignments) {

    CreateProjectCommand toCommand() {
        return new CreateProjectCommand(
                client,
                name,
                solution,
                engagement,
                contractMm,
                startDate,
                endDate,
                assignments.stream().map(AssignmentRequest::toSpec).toList());
    }
}
