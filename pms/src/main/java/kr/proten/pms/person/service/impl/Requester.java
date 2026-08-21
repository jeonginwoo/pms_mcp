package kr.proten.pms.person.service.impl;

import kr.proten.pms.person.service.entity.PermissionGroup;
import kr.proten.pms.person.service.entity.Person;

/**
 * 요청자 컨텍스트 — 호출자 personId를 판정에 필요한 본인·권한 그룹 쌍으로 해석한 결과.
 * 가시성(scope)과 기능 플래그 판정이 전부 이 쌍에서 출발한다.
 */
public record Requester(Person person, PermissionGroup group) {
}
