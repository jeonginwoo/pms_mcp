package kr.proten.pms.person.service.dto;

/**
 * 직급 1행 (US-E4) — 관리 화면이 쓰는 형태이자 등록 폼의 선택 목록이다.
 *
 * 구 `ReferenceItem`(id·이름)을 대체한다(2026-08-24): §7이 직급에 상세 라우트를 두지
 * 않으므로 이 목록이 곧 관리 화면의 원천인데, 수정(E4-2)은 계수와 version을 요구해
 * id·이름만으로는 폼을 채울 수도 보낼 수도 없었다. 선택 목록은 여분의 필드를
 * 무시하면 그만이지만, 반대 방향은 라우트를 하나 더 만들어야 한다.
 *
 * @param coeff       보정 가동률 가중치 — 바꾸면 매 조회 계산이라 다음 조회부터 반영된다(E4-2)
 * @param memberCount 이 직급을 쓰는 인원 수 — **비활성 포함**이다(삭제 거절 판정과 같은 기준)
 */
public record GradeDetail(Long id, String name, double coeff, long memberCount, long version) {
}
