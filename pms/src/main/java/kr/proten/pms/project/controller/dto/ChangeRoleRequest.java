package kr.proten.pms.project.controller.dto;

import jakarta.validation.constraints.NotNull;
import kr.proten.pms.project.service.entity.ProjectRole;

/**
 * 역할 지정·교체 요청 (AC A6-3) — `PUT /projects/{id}/roles`.
 *
 * <p>{@code version}을 받지 않는다: 바뀌는 행은 프로젝트가 아니라 배정이다(A6-3의 요청
 * 본문도 {@code {personId, role}} 둘뿐이다). PM 교체는 {@link ChangeManagerRequest}가
 * 전담하며 그쪽은 프로젝트 version을 받는다 — 그 경로는 {@code Project.managerId}를 함께
 * 바꾸기 때문이다.
 *
 * <p>{@code role=PM}은 서버가 {@code 422 INVALID_ROLE}로 거절한다(A6-7). 타입으로 막지
 * 않는 이유는 오류 문구가 "PM 교체를 쓰라"는 안내를 담아야 하기 때문이다 —
 * 역직렬화 실패로 400을 내면 그 안내를 줄 자리가 없다.
 */
public record ChangeRoleRequest(
        @NotNull(message = "대상 인원 id는 필수입니다") Long personId,
        @NotNull(message = "역할은 필수입니다") ProjectRole role) {
}
