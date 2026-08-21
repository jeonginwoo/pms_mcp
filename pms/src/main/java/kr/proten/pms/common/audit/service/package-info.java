/**
 * 감사 계층의 계약 (PRD-pms §4 common · EPIC G).
 *
 * 이 패키지는 모듈 공개 API다 — 다른 모듈은 {@link kr.proten.pms.common.audit.service.AuditTrail}과
 * 그 입력값(dto)만 참조한다. 구현·엔티티·저장소는 닫혀 있다.
 * action·source 열거는 계약의 어휘라 여기 둔다 — 엔티티 패키지에 두면 기록하는
 * 모듈이 common의 내부 영속 모델을 참조해야 한다.
 */
@NamedInterface("audit")
package kr.proten.pms.common.audit.service;

import org.springframework.modulith.NamedInterface;
