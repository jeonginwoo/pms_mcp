package kr.proten.pmsmock.port;

/**
 * 과부하 원인 배정 1건 — 어느 프로젝트에 몇 MM이 물려 있는지.
 */
public record CauseDto(String projectName, double mm) {}
