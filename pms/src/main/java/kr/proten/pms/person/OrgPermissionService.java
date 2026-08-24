package kr.proten.pms.person;

import java.util.List;

/**
 * 프로젝트 밖 기능 플래그 조회 (상위 PRD §4-3).
 * 권한 판정만 필요한 호출자를 위해 가시성 조회와 분리한다(ISP).
 */
public interface OrgPermissionService {

    /** 화자의 권한 그룹이 해당 플래그를 갖는가. */
    boolean has(long callerPersonId, OrgPermission permission);

    /**
     * 그 사람과 <b>같은 조직에 속하면서</b> 해당 플래그를 가진 인원 (AC F1-1 — 과부하 알림 수신자).
     *
     * <p><b>"팀장"을 이름이나 가시성 scope로 찾지 않는 이유</b>(2026-08-24 사용자 결정):
     * 권한 그룹은 2026-08-09 일반화 이후 사용자가 만들고 개명하고 지울 수 있는 데이터라
     * 이름으로 찾으면 E5로 개명하는 순간 알림이 조용히 멈춘다. 가시성 scope도 구분이
     * 되지 않는다 — 팀원이 2026-08-22에 SELF에서 TEAM으로 바뀌어 팀장과 같은 값이다.
     * 남는 안정된 표식이 <b>기능 플래그</b>이고, 상위 PRD §4-3이 "프로젝트 생성"을
     * 관리자·부문장·팀장에게만 주도록 이미 선을 그어 뒀다.
     *
     * <p>본인은 결과에서 뺀다 — 과부하 당사자가 팀장이면 자기 알림을 자기가 받는다.
     *
     * @return 활성 인원만. 대상이 없으면 빈 목록이다(그러면 알림을 만들지 않는다)
     */
    List<Long> findColleaguesWith(long personId, OrgPermission permission);
}
