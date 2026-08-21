/**
 * notification — 이벤트 → 인앱 알림 적재·SSE 즉시 푸시·마감 임박 스케줄러
 * (PRD-pms §4, EPIC E·F — PMS-M5). 이벤트는 사후 fan-out만(§0 규칙).
 */
@ApplicationModule
package kr.proten.pms.notification;

import org.springframework.modulith.ApplicationModule;
