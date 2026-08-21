package kr.proten.pms.person.service;

import java.util.List;
import kr.proten.pms.person.service.dto.OrgUnitView;

/**
 * 조직 트리 관리 유스케이스 — AC E3-3 (+ 관리 화면이 쓰는 목록).
 *
 * EPIC E 전체가 "사용자/조직/권한 관리" 플래그 전용이라(기본 그룹 중 관리자만)
 * 목록도 같은 판정을 거친다 — 조직 편집 화면 밖에서 쓰이는 목록이 아니다.
 * 신설·이름 변경(E3-1·E3-2)은 아직 범위 밖이다.
 */
public interface OrgUnitService {

    /** 조직 트리 전체 — 노드마다 소속 인원·하위 노드 수와 삭제 가능 여부를 함께 준다. */
    List<OrgUnitView> list(long callerPersonId);

    /** 노드를 만든다 (AC E3-1) — 임의 깊이 허용, parentId가 null이면 회사(root)다. */
    OrgUnitView create(long callerPersonId, Long parentId, String name);

    /** 빈 노드를 삭제한다 — 소속 인원이나 하위 노드가 있으면 거절한다 (AC E3-3). */
    void delete(long callerPersonId, long orgUnitId);
}
