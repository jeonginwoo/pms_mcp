package kr.proten.pms.project.service.dto;

import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.project.service.entity.ProjectPhase;

/**
 * 프로젝트 목록 항목 (AC A3-1).
 * 담당 PM 이름을 함께 담는다 — 목록에서 사람 이름을 보려고 인원 수만큼 개별
 * 조회를 반복하지 않게 하려는 것이다.
 *
 * <p>phase는 status 파생값이다(§5) — 단건 응답과 같은 이유로 목록도 서버가 실어 준다.
 * 빼면 phase 탭을 그리는 화면이 status → phase 표를 자기가 다시 갖게 되고, 그것이
 * §5가 금지한 이중화다. 유지보수중은 어느 그룹에도 들지 않아 null이다.
 */
public record ProjectSummary(
        Long id,
        String client,
        String name,
        ProjectStatus status,
        ProjectPhase phase,
        int progress,
        Long managerId,
        String managerName) {
}
