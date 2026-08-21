/**
 * common 모듈의 설정 계층 — 호출자 식별 배선.
 *
 * 각 모듈의 컨트롤러가 {@link kr.proten.pms.common.config.CallerPersonId}를 쓰므로
 * 모듈 공개 API다. 보안 체인·CORS는 인증 도입 시 여기에 들어온다(인증은
 * 2026-08-21 재구축 범위 밖).
 */
@NamedInterface("config")
package kr.proten.pms.common.config;

import org.springframework.modulith.NamedInterface;
