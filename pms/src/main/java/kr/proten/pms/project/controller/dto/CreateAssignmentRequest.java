package kr.proten.pms.project.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import kr.proten.pms.project.service.dto.CreateAssignmentCommand;
import kr.proten.pms.project.service.entity.ProjectRole;

/**
 * 인력 배정 요청 (AC B1-1).
 *
 * @param startDate 미지정이면 프로젝트 시작일로 채워진다 (A6-6 기본값)
 * @param endDate   미지정이면 프로젝트 종료일로 채워진다
 * @param monthlyMm 실투입 계획 M/M — 미지정이면 0 (상위 PRD §3: 계약 배분 아님)
 */
public record CreateAssignmentRequest(
        @NotNull(message = "참여자 id는 필수입니다") Long personId,
        @NotNull(message = "프로젝트 역할은 필수입니다") ProjectRole role,
        LocalDate startDate,
        LocalDate endDate,
        @PositiveOrZero(message = "배정 M/M은 0 이상이어야 합니다") Double monthlyMm) {

    public CreateAssignmentCommand toCommand(long projectId) {
        return new CreateAssignmentCommand(projectId, personId, role, startDate, endDate,
                monthlyMm == null ? 0.0 : monthlyMm);
    }
}
