package kr.proten.pms.project.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import kr.proten.pms.project.service.dto.UpdateProjectPermissionsCommand;
import kr.proten.pms.project.service.entity.ProjectAction;
import kr.proten.pms.project.service.entity.ProjectRole;

/**
 * 권한 매트릭스 조정 요청 (AC A8-2) — `PUT /projects/{id}/permissions`.
 *
 * <p><b>전체 교체</b>다. {@code overrides}가 비면 전체 기본값 복원이고(A8-2 — "별도
 * API 없음"), 그래서 {@code null}과 빈 목록을 구별해야 한다: {@code null}을 "안 바꾼다"로
 * 받으면 기본값 복원을 표현할 방법이 사라진다 → {@code @NotNull}로 명시를 요구한다.
 *
 * <p>{@code role}·{@code action}이 열거인 것은 모르는 이름을 §7 봉투 400으로 돌려주기
 * 위한 것이다(`?phase=` 선례). 실재하지만 <b>고정된</b> 칸은 그와 달리
 * {@code 422 IMMUTABLE_PERMISSION}이다 — "그런 칸은 없다"와 "그 칸은 못 바꾼다"는
 * 다른 답이어야 한다.
 */
public record UpdatePermissionsRequest(
        @NotNull(message = "overrides는 필수입니다 (빈 배열 = 전체 기본값 복원)")
        List<@Valid Override> overrides,
        @NotNull(message = "version은 필수입니다") Long version) {

    public record Override(
            @NotNull(message = "역할은 필수입니다") ProjectRole role,
            @NotNull(message = "기능은 필수입니다") ProjectAction action,
            @NotNull(message = "허용 여부는 필수입니다") Boolean allowed) {
    }

    public UpdateProjectPermissionsCommand toCommand() {
        return new UpdateProjectPermissionsCommand(
                overrides.stream()
                        .map(o -> new UpdateProjectPermissionsCommand.Override(
                                o.role(), o.action(), o.allowed()))
                        .toList(),
                version);
    }
}
