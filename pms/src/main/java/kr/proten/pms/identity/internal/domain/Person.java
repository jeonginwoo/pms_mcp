package kr.proten.pms.identity.internal.domain;

/**
 * 사람 — 조직·직급·권한 그룹 소속과 가동률 속성 (PRD-pms §4).
 *
 * @param capacity 월 가용 M/M 기본값 (1.0 = 풀타임)
 * @param billable 가동률 집계 모집단 여부 (상위 PRD §3 — 2026-08-06)
 * @param system   시스템 계정 플래그 — 삭제·수정 불가, 인력·가동률·배정 목록 제외 (2026-08-09 ④)
 * @param active   soft 삭제 상태 (E2-3 — false면 로그인 차단·목록 제외, 과거 데이터 보존)
 */
public record Person(
        Long id,
        String name,
        Long orgUnitId,
        Long gradeId,
        Long groupId,
        double capacity,
        boolean billable,
        boolean system,
        boolean active,
        long version) {
}
