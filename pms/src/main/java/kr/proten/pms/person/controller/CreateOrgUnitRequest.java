package kr.proten.pms.person.controller;

import jakarta.validation.constraints.NotBlank;

/**
 * 조직 노드 신설 요청 (AC E3-1).
 * parentId가 없으면 회사(root)를 만드는 요청이다 — 이미 root가 있으면 서버가 거절한다.
 */
public record CreateOrgUnitRequest(
        Long parentId,
        @NotBlank(message = "조직명은 필수입니다") String name) {
}
