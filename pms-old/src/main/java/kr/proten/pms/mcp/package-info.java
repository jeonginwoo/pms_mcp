/**
 * mcp — 임베디드 MCP 서버 어댑터 (구조 원칙 2 — 별도 프로세스 금지). 소유: MCP 담당.
 * pms-mcp-mock의 mcp/·port/ 승격분 (구현_노트 부록 B-3, 2026-08-17 모듈 결정).
 *
 * 모듈 루트 = 공개 계약: port 인터페이스 5종 + DTO + ToolError.
 * 각 도메인 모듈의 애플리케이션 서비스가 이 인터페이스를 구현한다(PMS-M1~) —
 * 시그니처 변경은 협업 접점(공용 결정 기록 경유). internal/은 도구·보안 체인,
 * internal/seed/는 서비스 구현 전까지의 임시 어댑터(교체 대상).
 */
@ApplicationModule
package kr.proten.pms.mcp;

import org.springframework.modulith.ApplicationModule;
