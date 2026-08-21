package kr.proten.pms.project.service.dto;

import java.time.LocalDate;
import kr.proten.pms.project.service.entity.ProjectRole;

/**
 * 배정 항목 (AC A3-3·B1-1·B1-4).
 * 프로젝트 컨텍스트 안에서는 타 팀 인원의 배정도 보인다 — 다만 그 인원의 다른
 * 프로젝트·전체 가동률은 조직 가시성 규칙을 그대로 따른다 (상위 PRD §4-4).
 *
 * @param id      배정 식별자 — 수정·종료 경로(`/api/assignments/{id}`)의 대상이다
 * @param version 낙관적 락 버전 (AC B1-4) — 프로젝트의 version과 별개다
 */
public record AssignmentView(
        Long id,
        Long personId,
        String personName,
        ProjectRole role,
        LocalDate startDate,
        LocalDate endDate,
        double monthlyMm,
        long version) {
}
