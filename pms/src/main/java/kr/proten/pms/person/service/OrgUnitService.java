package kr.proten.pms.person.service;

import java.util.List;
import kr.proten.pms.person.service.dto.OrgUnitView;

/**
 * 조직 트리 관리 유스케이스 — AC E3-1~E3-6 (+ 관리 화면이 쓰는 목록).
 *
 * EPIC E 전체가 "사용자/조직/권한 관리" 플래그 전용이라(기본 그룹 중 관리자만)
 * 목록도 같은 판정을 거친다 — 조직 편집 화면 밖에서 쓰이는 목록이 아니다.
 */
public interface OrgUnitService {

    /** 조직 트리 전체 — 노드마다 소속 인원·하위 노드 수와 삭제 가능 여부를 함께 준다. */
    List<OrgUnitView> list(long callerPersonId);

    /** 노드를 만든다 (AC E3-1) — 임의 깊이 허용, parentId가 null이면 회사(root)다. */
    OrgUnitView create(long callerPersonId, Long parentId, String name);

    /**
     * 노드 이름을 바꾼다 (AC E3-2).
     * 소속 인원·프로젝트는 orgUnitId로 참조하므로 표시가 저절로 따라온다 —
     * 이름을 복사해 둔 컬럼이 없는 것이 이 AC가 성립하는 이유다.
     */
    OrgUnitView rename(long callerPersonId, long orgUnitId, String name);

    /**
     * 노드를 다른 상위 조직 아래로 옮긴다 (AC E3-5·E3-6).
     *
     * 소속 인원·프로젝트는 orgUnitId로 참조하므로 함께 옮겨진다 — 개명(E3-2)과 같은
     * 원리다. 순환(자기 자신·자기 subtree)과 회사(root) 이동은 거절한다: 트리가 트리가
     * 아니게 되면 부문 가시성 계산(root 직계 자식 기준)이 성립하지 않는다.
     */
    OrgUnitView move(long callerPersonId, long orgUnitId, Long parentId);

    /** 빈 노드를 삭제한다 — 소속 인원이나 하위 노드가 있으면 거절한다 (AC E3-3). */
    void delete(long callerPersonId, long orgUnitId);
}
