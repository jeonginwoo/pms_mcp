package kr.proten.pms.person.service;

import java.util.List;
import kr.proten.pms.person.service.dto.ReferenceItem;

/**
 * 직급·권한 그룹 목록 (PRD-pms §7 `GET /api/grades`·`/api/permission-groups`의 조회 절반).
 *
 * 인력 등록 폼이 고를 목록이라 관리 화면과 같은 판정("사용자/조직/권한 관리" 플래그)을
 * 거친다. 등록·수정·삭제(US-E4·E5)는 아직 범위 밖이다.
 */
public interface ReferenceQueryService {

    List<ReferenceItem> grades(long callerPersonId);

    List<ReferenceItem> permissionGroups(long callerPersonId);
}
