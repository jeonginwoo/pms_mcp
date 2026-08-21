package kr.proten.pms.person.service;

import kr.proten.pms.person.service.dto.CreatePersonCommand;
import kr.proten.pms.person.service.dto.PersonRef;

/**
 * 인력 관리 유스케이스 — AC E2-1·E2-3~E2-5.
 *
 * 판정자는 권한 그룹의 "사용자/조직/권한 관리" 플래그다(기본 그룹 중 관리자만 —
 * 상위 PRD §4-3). 인력 수정(E2-2)은 아직 범위 밖이다.
 */
public interface PersonCommandService {

    /** 인원을 등록하고 로그인 계정을 함께 만든다 (AC E2-1). */
    PersonRef create(long callerPersonId, CreatePersonCommand command);

    /** 인원을 비활성한다 — 삭제가 아니라 soft 비활성이다 (AC E2-3). */
    void deactivate(long callerPersonId, long personId);
}
