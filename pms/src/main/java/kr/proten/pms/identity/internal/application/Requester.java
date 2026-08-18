package kr.proten.pms.identity.internal.application;

import kr.proten.pms.identity.internal.domain.PermissionGroup;
import kr.proten.pms.identity.internal.domain.Person;

/**
 * 요청자 컨텍스트 — 토큰의 personId를 판정에 필요한 본인·권한 그룹 쌍으로 해석한 결과.
 * 가시성(scope)·기능 플래그(orgPerm) 판정이 전부 이 쌍에서 출발한다.
 */
public record Requester(Person person, PermissionGroup group) {
}
