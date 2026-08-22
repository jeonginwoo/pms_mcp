package kr.proten.pms.person.service;

import java.util.List;
import kr.proten.pms.person.PersonRef;
import kr.proten.pms.person.service.dto.CreatePersonCommand;
import kr.proten.pms.person.service.dto.MeView;
import kr.proten.pms.person.service.dto.UpdatePersonCommand;

/**
 * 인력 유스케이스 — 조회·등록·수정·소속 이동·비활성 (AC E1-1 · E2-1~E2-5 · H1-1).
 *
 * 한 사람이라는 같은 대상에 대한 CRUD라 한 계약이다. 이전에는 조회·명령·내 계정이
 * 각각 인터페이스였는데, 셋 다 소비자가 컨트롤러 하나뿐이고 협력자도 같아서
 * 분리가 결합을 줄이지 못했다(2026-08-22 정리).
 *
 * 모듈 밖으로 나가는 질의는 여기 없다 — 다른 모듈이 쓰는 것은
 * `PersonDirectoryService`(참조 검증)·`OrgVisibilityService`(가시성)·
 * `OrgPermissionService`(플래그) 셋이고, 그 셋은 소비자가 서로 달라 분리가
 * 값을 하므로 그대로 둔다.
 *
 * 조회의 판정은 가시성이고(404 은닉), 쓰기의 판정은 권한 그룹의
 * "사용자/조직/권한 관리" 플래그다(403).
 */
public interface PersonService {

    /** 가시성 범위 내 인원 목록 — 시스템 계정·비활성 인원은 제외한다. */
    List<PersonRef> listVisible(long callerPersonId);

    /** 인원 단건 조회 — 노출 대상이 아닌 인원과 가시성 밖 인원은 같은 404다. */
    PersonRef getPerson(long callerPersonId, long personId);

    /**
     * 화자 자신의 신원과 권한 그룹 플래그 (AC H1-1 · MCP `whoami`와 같은 서비스).
     * 본인은 가시성 scope가 SELF여도 언제나 조회 대상이다.
     */
    MeView me(long callerPersonId);

    /** 인원을 등록하고 로그인 계정을 함께 만든다 (AC E2-1). */
    PersonRef create(long callerPersonId, CreatePersonCommand command);

    /**
     * 인원 정보를 수정한다 (AC E2-2) — 이름·소속·직급·권한 그룹.
     * 시스템 계정은 `422 IMMUTABLE_ACCOUNT`다 (E2-5 — 수정도 삭제와 같이 막힌다).
     */
    PersonRef update(long callerPersonId, UpdatePersonCommand command);

    /**
     * 소속 조직만 옮긴다 (AC E1-1) — 가시성이 즉시 따라 바뀐다.
     *
     * 수정(E2-2)과 따로 두는 이유: 조직 이동은 진행 중 배정이 있어도 허용하는 별도
     * 규칙을 갖고(E1-2), 과거 집계는 시점을 보존하지 않는다는 성질이 이 행위에만 붙는다.
     */
    PersonRef moveOrgUnit(long callerPersonId, long personId, long orgUnitId);

    /** 인원을 비활성한다 — 삭제가 아니라 soft 비활성이다 (AC E2-3). */
    void deactivate(long callerPersonId, long personId);
}
