package kr.proten.pms.project.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import kr.proten.pms.project.service.dto.UpdateProgressCommand;

/**
 * 진척률 갱신 요청 (AC A2-1·A2-2) — 2단계 확인 프로토콜의 본문.
 * 대상 프로젝트는 경로가 정하므로 본문에 두지 않는다.
 *
 * @param version   낙관적 락 버전 — 확인 단계에서만 검사한다 (AC A2-6)
 * @param confirmed false면 변경 요약만 돌려주고 저장하지 않는다
 */
public record UpdateProgressRequest(
        @NotNull(message = "진척률은 필수입니다")
        @Min(value = 0, message = "진척률은 0에서 100 사이여야 합니다")
        @Max(value = 100, message = "진척률은 0에서 100 사이여야 합니다") Integer progress,
        @NotNull(message = "version은 필수입니다") Long version,
        boolean confirmed) {

    public UpdateProgressCommand toCommand(long projectId) {
        return new UpdateProgressCommand(projectId, progress, version, confirmed);
    }
}
