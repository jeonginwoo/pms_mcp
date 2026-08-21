package kr.proten.pms.project.service.impl;

import java.time.LocalDate;
import kr.proten.pms.project.service.dto.AssignmentSpec;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAssignment;
import org.springframework.stereotype.Component;

/**
 * 배정 생성 — 지정 입력에 프로젝트 기본값을 채워 넣는다 (AC A1-4·A6-6·B1-1).
 *
 * 프로젝트 생성 시의 참여자와 나중에 추가하는 배정이 같은 기본값(기간 = 프로젝트
 * 기간)을 써야 하므로 한 곳에 둔다 — 입구마다 채우면 두 경로의 기본값이 갈라진다.
 */
@Component
class AssignmentFactory {

    ProjectAssignment create(Project project, AssignmentSpec spec) {
        return ProjectAssignment.of(
                project.getId(),
                spec.personId(),
                spec.role(),
                orDefault(spec.startDate(), project.getStartDate()),
                orDefault(spec.endDate(), project.getEndDate()),
                spec.monthlyMm());
    }

    private LocalDate orDefault(LocalDate value, LocalDate fallback) {
        return value == null ? fallback : value;
    }
}
