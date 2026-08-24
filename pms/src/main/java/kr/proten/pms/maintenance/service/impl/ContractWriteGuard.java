package kr.proten.pms.maintenance.service.impl;

import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import org.springframework.stereotype.Component;

/**
 * US-D2 공통 판정 — "계약 관리" 플래그 (상위 PRD §4-3: 관리자·부문장·팀장).
 *
 * 계약은 프로젝트 밖 행위라 프로젝트 역할이 판정하지 않는다(D2-3) — 유지보수
 * 계약에는 PM도 참여자도 없다. 판단 하나당 클래스 하나라는 배치를 따르고
 * (person의 {@code OrgManagePermission} · project의 {@code ProjectActionPermission}),
 * 네 쓰기 경로가 같은 관문을 지나므로 관문도 하나다. 유스케이스마다 같은 if를
 * 복사하면 한 곳을 빠뜨려도 아무 테스트가 깨지지 않는다 — EPIC E에서 실제로 그랬다.
 *
 * 조회는 이 관문을 지나지 않는다: 유지보수 조회는 전사 공개다(D4-3).
 */
@Component
class ContractWriteGuard {
    private final OrgPermissionService orgPermissionService;

    ContractWriteGuard(OrgPermissionService orgPermissionService) {
        this.orgPermissionService = orgPermissionService;
    }

    /** 플래그가 없으면 403. 계약·사이트·연락처 쓰기는 전부 이 관문을 먼저 지난다. */
    void require(long callerPersonId) {
        if (!orgPermissionService.has(callerPersonId, OrgPermission.MANAGE_CONTRACTS)) {
            throw new ForbiddenException("유지보수 계약 관리 권한이 없습니다");
        }
    }
}
