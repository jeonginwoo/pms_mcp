package kr.proten.pms.person;

import kr.proten.pms.person.OrgPermission;

/**
 * 프로젝트 밖 기능 플래그 조회 (상위 PRD §4-3).
 * 권한 판정만 필요한 호출자를 위해 가시성 조회와 분리한다(ISP).
 */
public interface OrgPermissionService {

    /** 화자의 권한 그룹이 해당 플래그를 갖는가. */
    boolean has(long callerPersonId, OrgPermission permission);
}
