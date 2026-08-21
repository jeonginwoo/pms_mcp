package kr.proten.pms.identity.internal.domain;

/**
 * 직급 — 이름과 보정 가동률 계수(coeff). 시드 값은 PRD-pms 부록 B, 편집은 US-E4.
 */
public record Grade(Long id, String name, double coeff, long version) {
}
