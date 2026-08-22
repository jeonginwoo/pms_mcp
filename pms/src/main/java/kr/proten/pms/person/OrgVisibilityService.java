package kr.proten.pms.person;

import kr.proten.pms.person.OrgVisibility;

/**
 * 조직 가시성 조회 — "이 화자에게 누가 보이는가"를 묻는 유일한 경로 (상위 PRD §4-4).
 * 가시성만 필요한 호출자가 인원 조회·권한 플래그까지 끌고 오지 않도록 좁게 나눈
 * 인터페이스다(ISP).
 */
public interface OrgVisibilityService {

    /** 화자의 조직 가시성. 화자가 없거나 비활성이면 404 은닉으로 수렴한다. */
    OrgVisibility visibilityOf(long callerPersonId);
}
