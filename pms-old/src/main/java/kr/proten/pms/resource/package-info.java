/**
 * resource — 가동률(기본=실투입 계획, 보정=단가 가중 보조 지표)·오버부킹 감지·
 * 이벤트 재계산 (PRD-pms §4, EPIC C — PMS-M3). 집계 모집단은 Person.billable.
 */
@ApplicationModule
package kr.proten.pms.resource;

import org.springframework.modulith.ApplicationModule;
