package kr.proten.pms.project.service.dto;

import java.time.LocalDate;
import kr.proten.pms.project.service.entity.ProjectRole;

/**
 * 인력 배정 입력 (AC B1-1).
 *
 * role에 PM은 올 수 없다 — PM 교체는 전용 경로(US-A6 `/pm`)만의 몫이라
 * 여기로 오면 422다(A6-7과 같은 이유: PM 1행 불변식 우회 차단).
 *
 * @param startDate 미지정(null)이면 프로젝트 시작일로 채운다 (A6-6 기본값)
 * @param endDate   미지정(null)이면 프로젝트 종료일로 채운다
 * @param monthlyMm 실투입 계획 M/M — 계약 배분 숫자가 아니다 (상위 PRD §3 · B1-5)
 */
public record CreateAssignmentCommand(
        long projectId,
        Long personId,
        ProjectRole role,
        LocalDate startDate,
        LocalDate endDate,
        double monthlyMm) {

    /** 배정 생성 규칙은 프로젝트 생성 시의 참여자 지정과 같다 — 같은 값으로 옮긴다. */
    public AssignmentSpec toSpec() {
        return new AssignmentSpec(personId, role, startDate, endDate, monthlyMm);
    }
}
