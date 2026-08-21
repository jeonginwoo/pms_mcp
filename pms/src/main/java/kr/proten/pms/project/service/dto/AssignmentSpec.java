package kr.proten.pms.project.service.dto;

import java.time.LocalDate;
import kr.proten.pms.project.service.entity.ProjectRole;

/**
 * 배정 지정 입력 (AC A1-4) — 프로젝트 생성 시 함께 넘어오는 참여자 한 명.
 *
 * @param startDate 미지정(null)이면 프로젝트 시작일로 채운다
 * @param endDate   미지정(null)이면 프로젝트 종료일로 채운다
 * @param monthlyMm 실투입 계획 M/M — 계약 배분 숫자가 아니다 (상위 PRD §3)
 */
public record AssignmentSpec(
        Long personId,
        ProjectRole role,
        LocalDate startDate,
        LocalDate endDate,
        double monthlyMm) {
}
