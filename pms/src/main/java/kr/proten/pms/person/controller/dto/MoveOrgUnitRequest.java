package kr.proten.pms.person.controller.dto;

import jakarta.validation.constraints.NotNull;

/** 소속 조직 이동 요청 (AC E1-1). */
public record MoveOrgUnitRequest(
        @NotNull(message = "이동할 조직은 필수입니다") Long orgUnitId) {
}
