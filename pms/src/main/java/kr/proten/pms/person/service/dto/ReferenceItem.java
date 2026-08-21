package kr.proten.pms.person.service.dto;

/**
 * 참조 데이터 항목 — 직급·권한 그룹처럼 "id와 이름"만 필요한 선택 목록용.
 *
 * 직급의 계수(coeff)나 그룹의 플래그를 담지 않는다: 인력 등록 폼이 필요한 것은
 * 고를 수 있는 목록뿐이고, 그 값들은 각자의 관리 화면(US-E4·E5)이 다룰 몫이다.
 */
public record ReferenceItem(Long id, String name) {
}
