package kr.proten.pms.project.service.dto;

import java.time.LocalDate;
import kr.proten.pms.project.service.entity.Engagement;
import kr.proten.pms.project.service.entity.ProjectStatus;

/**
 * 프로젝트 정보·상태 수정 입력 (AC A5-1) — 전체 치환(PUT) 의미론이다.
 *
 * managerId가 없는 것이 규칙이다: PM 교체는 배정 역할 이동을 동반하므로 전용
 * 경로(US-A6 `/pm`)만의 몫이다. 진척률도 없다 — 2단계 확인이 붙은 별도 경로(US-A2)다.
 *
 * @param status  현재 상태를 그대로 주면 정보만 바뀐다 — 순방향 한 칸 외의 값은 409
 * @param version 낙관적 락 버전 — `Project.version` 공용 (AC A8-7과 같은 값)
 */
public record EditProjectCommand(
        long projectId,
        String client,
        String name,
        String solution,
        Engagement engagement,
        double contractMm,
        LocalDate startDate,
        LocalDate endDate,
        ProjectStatus status,
        long version) {
}
