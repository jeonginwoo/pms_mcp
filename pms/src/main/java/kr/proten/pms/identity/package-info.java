/**
 * identity — 조직 트리(OrgUnit)·직급(Grade)·권한 그룹(PermissionGroup)·
 * 사람(Person)·계정(User). 조직 가시성 판정의 소유 모듈 (PRD-pms §2·§4).
 * 레이어 골격은 첫 구현(PMS-M1)과 함께: 모듈 루트 = 공개 API,
 * internal/{application,domain,web} (conventions/java-spring.md §5).
 */
@ApplicationModule
package kr.proten.pms.identity;

import org.springframework.modulith.ApplicationModule;
