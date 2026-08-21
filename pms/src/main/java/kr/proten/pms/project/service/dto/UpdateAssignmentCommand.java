package kr.proten.pms.project.service.dto;

import java.time.LocalDate;

/**
 * 배정 수정 입력 (AC B1-4) — 기간과 투입 M/M만이다.
 * 역할 변경은 전용 경로(US-A6 `/roles`), 배정 종료는 DELETE(US-B2)가 맡는다.
 *
 * @param version 낙관적 락 버전 — `ProjectAssignment.version`이다(프로젝트 것과 별개)
 */
public record UpdateAssignmentCommand(
        long assignmentId,
        LocalDate startDate,
        LocalDate endDate,
        double monthlyMm,
        long version) {
}
