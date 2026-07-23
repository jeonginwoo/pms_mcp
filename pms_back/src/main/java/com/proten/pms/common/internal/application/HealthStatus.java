package com.proten.pms.common.internal.application;

/**
 * 시스템 헬스 상태 응답. LLM/화면에 노출해도 안전한 필드만 담는다.
 * status는 UP 또는 DOWN, database는 DB 왕복 성공 여부.
 */
public record HealthStatus(String status, boolean database) {
}
