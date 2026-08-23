package kr.proten.pms.person.service.dto;

/**
 * 권한 그룹 등록·수정 입력 (AC E5-1·E5-2).
 *
 * `systemFixed`가 없는 것은 의도다 — 시스템 고정은 시드가 정하는 성질이고
 * 요청으로 켜고 끌 수 있으면 자기 잠금 방지 자체가 무의미해진다(E5-3).
 *
 * @param groupId         수정 대상 — 등록이면 null
 * @param visibilityScope 모르는 값이면 `422`(§7 — 열거 값 위반은 형식이 아니라 의미 오류)
 */
public record PermissionGroupCommand(
        Long groupId,
        String name,
        String visibilityScope,
        boolean createProject,
        boolean manageContracts,
        boolean manageAllProjects,
        boolean manageOrg,
        long version) {
}
