package kr.proten.pms.project.service.dto;

import kr.proten.pms.project.ProjectStatus;

/**
 * 프로젝트 목록 항목 (AC A3-1).
 * 담당 PM 이름을 함께 담는다 — 목록에서 사람 이름을 보려고 인원 수만큼 개별
 * 조회를 반복하지 않게 하려는 것이다.
 */
public record ProjectSummary(
        Long id,
        String client,
        String name,
        ProjectStatus status,
        int progress,
        Long managerId,
        String managerName) {
}
