package kr.proten.pms.mcp;

/**
 * 본인 식별 (FR-AI-16) — 유효 권한 미반환(2026-08-03 결정).
 * permissionGroup = 권한 그룹명 (orgRole 대체 — 2026-08-09 결정 ⑦).
 */
public record WhoamiResult(int id, String name, String team, String division, String permissionGroup) {
}
