package kr.proten.pms.person.service.dto;

/**
 * 직급 등록·수정 입력 (AC E4-1·E4-2).
 *
 * @param gradeId 수정 대상 — 등록이면 null
 * @param version 낙관적 락 — 등록이면 무시된다
 */
public record GradeCommand(Long gradeId, String name, double coeff, long version) {
}
