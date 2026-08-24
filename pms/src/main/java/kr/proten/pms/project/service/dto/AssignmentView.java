package kr.proten.pms.project.service.dto;

import java.time.LocalDate;
import kr.proten.pms.project.service.entity.ProjectRole;

/**
 * 배정 항목 (AC A3-3·B1-1·B1-4).
 * 프로젝트 컨텍스트 안에서는 타 팀 인원의 배정도 보인다 — 다만 그 인원의 다른
 * 프로젝트·전체 가동률은 조직 가시성 규칙을 그대로 따른다 (상위 PRD §4-4).
 *
 * @param id           배정 식별자 — 수정·종료 경로(`/api/assignments/{id}`)의 대상이다
 * @param personActive 그 사람이 아직 재직 중인가 (2026-08-24 신설) — 배정은 종료돼도
 *                     행이 남으므로(B2-1) 퇴사자의 배정이 상세에 남는다. 이름을 주지
 *                     않으면 화면이 {@code #17}을 그리게 되므로 이름은 항상 주고,
 *                     그 사람이 이미 없다는 사실을 이 플래그로 알린다
 * @param version      낙관적 락 버전 (AC B1-4) — 프로젝트의 version과 별개다
 */
public record AssignmentView(
        Long id,
        Long personId,
        String personName,
        boolean personActive,
        ProjectRole role,
        LocalDate startDate,
        LocalDate endDate,
        double monthlyMm,
        long version) {
}
