package kr.proten.pms.project.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import kr.proten.pms.project.service.dto.EditProjectCommand;
import kr.proten.pms.project.service.entity.Engagement;
import kr.proten.pms.project.service.entity.ProjectStatus;

/**
 * 프로젝트 정보·상태 수정 요청 (AC A5-1) — 전체 치환(PUT) 본문.
 *
 * status를 본문에 받는 이유는 화면의 수정 폼이 상태 뱃지를 함께 다루기 때문이고,
 * 자유 편집이 되지 않도록 허용 전이는 서버가 판정한다(순방향 한 칸 — §5).
 * 대상 프로젝트는 경로가 정하므로 본문에 두지 않는다.
 */
public record EditProjectRequest(
        @NotBlank(message = "고객사는 필수입니다") String client,
        @NotBlank(message = "프로젝트명은 필수입니다") String name,
        String solution,
        @NotNull(message = "수행 형태는 필수입니다") Engagement engagement,
        @NotNull(message = "계약 M/M은 필수입니다")
        @PositiveOrZero(message = "계약 M/M은 0 이상이어야 합니다") Double contractMm,
        LocalDate startDate,
        LocalDate endDate,
        @NotNull(message = "상태는 필수입니다") ProjectStatus status,
        @NotNull(message = "version은 필수입니다") Long version) {

    EditProjectCommand toCommand(long projectId) {
        return new EditProjectCommand(
                projectId,
                client,
                name,
                solution,
                engagement,
                contractMm,
                startDate,
                endDate,
                status,
                version);
    }
}
