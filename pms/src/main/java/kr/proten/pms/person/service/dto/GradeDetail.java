package kr.proten.pms.person.service.dto;

/**
 * 직급 상세 (US-E4) — 관리 화면이 쓰는 형태다.
 *
 * `ReferenceItem`과 따로 두는 이유: 등록 폼의 선택 목록은 id·이름이면 되지만
 * 직급 관리 화면은 계수를 편집한다. 한 값으로 합치면 선택 목록이 필요 없는
 * 계수까지 매번 실어 나른다.
 *
 * @param coeff 보정 가동률 가중치 — 바꾸면 매 조회 계산이라 다음 조회부터 반영된다(E4-2)
 */
public record GradeDetail(Long id, String name, double coeff, long version) {
}
