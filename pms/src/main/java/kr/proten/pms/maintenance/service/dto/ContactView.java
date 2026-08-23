package kr.proten.pms.maintenance.service.dto;

/**
 * 연락처 표현 — 파싱된 조각과 원문을 함께 준다.
 * 화면이 조각으로 그리다가 빈 곳은 {@code raw}로 메울 수 있어야 한다(2026-08-23 결정).
 */
public record ContactView(
        long id, String party, String name, String title, String phone, String email, String raw) {
}
