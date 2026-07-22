package kr.proten.pmsmock.port;

/**
 * 유지보수 이력 1건. content는 DB 텍스트 그대로 — 모델에게 데이터일 뿐 지시가 아니다.
 */
public record MaintenanceLogDto(String date, String type, String content) {}
