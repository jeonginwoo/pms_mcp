package kr.proten.pms.person.service.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import kr.proten.pms.person.service.dto.OrgPermission;

/**
 * person 모듈 테스트 픽스처 — 참조 데이터(조직·직급·권한 그룹·인원) 생성 단일 지점.
 * 트리: 프로텐(1) → 솔루션사업부(2) → SI팀(3) → SI-1파트(4) · CS팀(5) / AX사업기획부(6)
 */
public final class PersonFixtures {
    public static final long COMPANY_ID = 1L;
    public static final long DIVISION_ID = 2L;
    public static final long SI_TEAM_ID = 3L;
    public static final long SI_PART_ID = 4L;
    public static final long CS_TEAM_ID = 5L;
    public static final long OTHER_DIVISION_ID = 6L;

    private PersonFixtures() {
    }

    /** 임의 깊이 트리 — subtree·최상위 부문 판정 검증용. */
    public static List<OrgUnit> orgUnits() {
        return List.of(
                OrgUnit.of(COMPANY_ID, null, "프로텐"),
                OrgUnit.of(DIVISION_ID, COMPANY_ID, "솔루션사업부"),
                OrgUnit.of(SI_TEAM_ID, DIVISION_ID, "SI팀"),
                OrgUnit.of(SI_PART_ID, SI_TEAM_ID, "SI-1파트"),
                OrgUnit.of(CS_TEAM_ID, DIVISION_ID, "CS팀"),
                OrgUnit.of(OTHER_DIVISION_ID, COMPANY_ID, "AX사업기획부"));
    }

    public static OrgTree orgTree() {
        return OrgTree.of(orgUnits());
    }

    public static Grade grade(long id, String name, double coeff) {
        return Grade.of(id, name, coeff);
    }

    /** 가시성 scope만 다른 그룹 — 기능 플래그는 전부 off. */
    public static PermissionGroup group(long id, String name, VisibilityScope scope) {
        return group(id, name, scope, new OrgPermission[0]);
    }

    /**
     * 기능 플래그를 지정한 그룹 (상위 PRD §4-3).
     * 엔티티는 플래그를 개별 컬럼으로 갖지만(§4) 테스트는 열거가 읽기 쉬워 varargs로 받는다.
     */
    public static PermissionGroup group(
            long id,
            String name,
            VisibilityScope scope,
            OrgPermission... permissions) {
        Set<OrgPermission> granted = permissions.length == 0
                ? EnumSet.noneOf(OrgPermission.class)
                : EnumSet.copyOf(List.of(permissions));

        return PermissionGroup.of(
                id,
                name,
                scope,
                granted.contains(OrgPermission.CREATE_PROJECT),
                granted.contains(OrgPermission.MANAGE_CONTRACTS),
                granted.contains(OrgPermission.MANAGE_ALL_PROJECTS),
                granted.contains(OrgPermission.MANAGE_ORG),
                false);
    }

    /** 집계 대상(billable) 일반 인원. */
    public static Person person(long id, String name, long orgUnitId, long groupId) {
        return Person.of(id, name, orgUnitId, 1L, groupId, 1.0, true, false, true);
    }

    /** 시스템 계정 — 인력 목록·단건에서 제외되어야 한다 (상위 PRD §4-3). */
    public static Person systemAccount(long id, long orgUnitId, long groupId) {
        return Person.of(id, "시스템관리자", orgUnitId, 1L, groupId, 0.0, false, true, true);
    }

    /** soft 삭제(비활성) 인원 — 목록·단건에서 제외되어야 한다. */
    public static Person inactive(long id, String name, long orgUnitId, long groupId) {
        return Person.of(id, name, orgUnitId, 1L, groupId, 1.0, true, false, false);
    }
}
