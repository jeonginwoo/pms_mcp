package kr.proten.pms.person.service.dto;

/**
 * 권한 그룹 1행 (US-E5) — 가시성 scope + 기능 플래그 4종 (상위 PRD §4-3).
 *
 * 구 `ReferenceItem`을 대체한다(2026-08-24 — `GradeDetail`과 같은 이유): 부록 A의
 * 권한 그룹 행은 "n명 · [권한 ▾] 펼침(가시성 select + 기능 토글) · [수정] ·
 * 인원 0일 때만 [삭제]"인데, id·이름만으로는 그 어느 것도 그릴 수 없었다.
 *
 * @param systemFixed 시스템 고정 그룹(관리자) — 수정·삭제가 422다. 화면은 버튼을 잠근다
 * @param memberCount 이 그룹에 속한 인원 수 — **비활성 포함**(삭제 거절 판정과 같은 기준)
 */
public record PermissionGroupDetail(
        Long id,
        String name,
        String visibilityScope,
        boolean createProject,
        boolean manageContracts,
        boolean manageAllProjects,
        boolean manageOrg,
        boolean systemFixed,
        long memberCount,
        long version) {
}
