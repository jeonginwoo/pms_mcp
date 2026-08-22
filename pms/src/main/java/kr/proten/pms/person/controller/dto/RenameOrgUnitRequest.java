package kr.proten.pms.person.controller.dto;

import jakarta.validation.constraints.NotBlank;

/** 조직 노드 개명 요청 (AC E3-2). */
public record RenameOrgUnitRequest(
        @NotBlank(message = "이름은 필수입니다") String name) {
}
