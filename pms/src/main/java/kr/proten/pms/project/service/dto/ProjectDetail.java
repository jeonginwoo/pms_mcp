package kr.proten.pms.project.service.dto;

import java.time.LocalDate;
import java.util.List;
import kr.proten.pms.project.service.entity.Engagement;
import kr.proten.pms.project.service.entity.ProjectPhase;
import kr.proten.pms.project.service.entity.ProjectStatus;

/**
 * 프로젝트 상세 (AC A3-2·A3-3).
 * phase는 status 파생값이다(§5·§7). PM 이름을 따로 담지 않는다 — PM은 항상 배정 인원이므로(상위 PRD §4-2)
 * assignments 안에 이미 있고, 같은 값을 두 곳에 실으면 갈라진다.
 */
public record ProjectDetail(
        Long id,
        String client,
        String name,
        String solution,
        Engagement engagement,
        ProjectStatus status,
        ProjectPhase phase,
        int progress,
        double contractMm,
        LocalDate startDate,
        LocalDate endDate,
        Long managerId,
        long version,
        List<AssignmentView> assignments) {
}
