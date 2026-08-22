package kr.proten.pms.person.service;

import java.util.List;
import kr.proten.pms.person.service.dto.PermissionGroupCommand;
import kr.proten.pms.person.service.dto.PermissionGroupDetail;
import kr.proten.pms.person.service.dto.ReferenceItem;

/**
 * 권한 그룹 관리 유스케이스 — US-E5 (2026-08-09 ⑦ 채택. 규칙 원본은 상위 PRD §4-3).
 *
 * 이 그룹 정의가 곧 판정·가시성·404 은닉의 기준이므로, 수정 결과는 다음 요청부터
 * 그대로 적용된다 — 별도의 반영 절차가 없는 것이 정상이다.
 * 관리자 그룹만 시스템 고정이다(E5-3) — 자기 잠금 방지.
 */
public interface PermissionGroupService {

    /** 선택 목록 — 인력 등록 폼이 고를 그룹들. 관리 화면과 같은 판정을 거친다. */
    List<ReferenceItem> list(long callerPersonId);

    /** 그룹을 만든다 (AC E5-1). */
    PermissionGroupDetail create(long callerPersonId, PermissionGroupCommand command);

    /** 그룹을 수정한다 (AC E5-2) — 관리자 그룹은 `422 IMMUTABLE_GROUP`. */
    PermissionGroupDetail update(long callerPersonId, PermissionGroupCommand command);

    /**
     * 그룹을 삭제한다 — 소속 인원이 있으면 `409 IN_USE`(E5-4),
     * 관리자 그룹이면 `422 IMMUTABLE_GROUP`(E5-3).
     */
    void delete(long callerPersonId, long groupId);
}
