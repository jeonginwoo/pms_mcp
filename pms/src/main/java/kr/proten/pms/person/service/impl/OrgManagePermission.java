package kr.proten.pms.person.service.impl;

import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import org.springframework.stereotype.Component;

/**
 * EPIC E 공통 판정 — "사용자/조직/권한 관리" 플래그 (상위 PRD §4-3).
 *
 * 인력·조직·직급·권한 그룹·통합 감사 로그가 전부 같은 하나의 플래그로 열리므로
 * 판정도 한 곳에 둔다(project 쪽 `ProjectActionPermission`과 같은 이유 —
 * 판단 하나당 클래스 하나). 유스케이스마다 같은 if를 복사하면 **한 곳을 빠뜨려도
 * 아무 테스트가 깨지지 않는다** — 실제로 조직 개명(E3-2) 골격이 그렇게 빠져
 * 관리 권한 없는 호출자에게 403 대신 501을 내주고 있었다(2026-08-22 리뷰 발견).
 */
@Component
public class OrgManagePermission {
    private final OrgPermissionService orgPermissionService;

    public OrgManagePermission(OrgPermissionService orgPermissionService) {
        this.orgPermissionService = orgPermissionService;
    }

    /** 플래그가 없으면 403. 조회·쓰기 어느 쪽이든 EPIC E는 이 관문을 먼저 지난다. */
    public void require(long callerPersonId) {
        if (!orgPermissionService.has(callerPersonId, OrgPermission.MANAGE_ORG)) {
            throw new ForbiddenException("사용자·조직 관리 권한이 없습니다");
        }
    }
}
