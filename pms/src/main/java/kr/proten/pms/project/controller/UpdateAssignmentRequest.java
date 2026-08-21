package kr.proten.pms.project.controller;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import kr.proten.pms.project.service.dto.UpdateAssignmentCommand;

/**
 * 배정 수정 요청 (AC B1-4) — 기간과 투입 M/M만이다.
 * 역할은 여기 없다: 역할 지정·해제는 전용 경로(US-A6 `/roles`)의 몫이다.
 */
public record UpdateAssignmentRequest(
        LocalDate startDate,
        LocalDate endDate,
        @NotNull(message = "배정 M/M은 필수입니다")
        @PositiveOrZero(message = "배정 M/M은 0 이상이어야 합니다") Double monthlyMm,
        @NotNull(message = "version은 필수입니다") Long version) {

    UpdateAssignmentCommand toCommand(long assignmentId) {
        return new UpdateAssignmentCommand(assignmentId, startDate, endDate, monthlyMm, version);
    }
}
