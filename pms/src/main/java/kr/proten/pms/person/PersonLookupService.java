package kr.proten.pms.person;

import java.util.List;

/**
 * 인원 조회 — 모듈 밖 소비자용 (현재 소비자: `/mcp` 어댑터의 whoami·find_person).
 *
 * 루트에 올린 이유: 공개 계약은 모듈 루트이고(PRD-pms §0), 이 계약은 **소비자가
 * 다르다**는 기준을 충족한다(conventions §5 "a distinct consumer") — `PersonService`는
 * 컨트롤러가 쓰는 인력 CRUD 계약이고 쓰기까지 담고 있어, 조회만 필요한 어댑터에
 * 그대로 내보내면 필요 없는 쓰기 유스케이스까지 경계를 넘는다.
 * 같은 이유로 갈라 둔 이웃이 `PersonDirectoryService`·`OrgVisibilityService`·
 * `OrgPermissionService`다.
 *
 * 가시성 판정은 이 계층에서 끝난다 — 어댑터는 호출만 한다(구조 원칙 3).
 */
public interface PersonLookupService {

    /** 화자 본인의 신원. 유효 권한은 반환하지 않는다. 없거나 비활성인 화자는 404로 수렴한다. */
    PersonIdentity identityOf(long callerPersonId);

    /**
     * 가시성 범위 내 인원 검색 — name·team 부분 일치, 둘 다 null·공백 허용(미지정).
     * 범위 산출은 `PersonService.listVisible`과 같은 경로다 — 챗과 화면이 같은 답을
     * 내야 하므로 판정을 복제하지 않는다.
     */
    List<PersonRef> search(long callerPersonId, String name, String team);
}
