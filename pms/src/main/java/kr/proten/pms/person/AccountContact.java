package kr.proten.pms.person;

/**
 * 계정의 연락처 — person이 {@code AccountPort}로 받아 오는 값 (AC H1-1·H1-2).
 *
 * <p><b>person의 어휘로 좁혀 놨다</b>: auth의 {@code User}에는 비밀번호 해시·id·version이
 * 함께 있지만 person이 알 이유가 없는 것들이다. 역포트의 입력·출력은 정의하는 쪽의
 * 어휘여야 하고({@code AccountPort} 주석), 그러지 않으면 person이 auth의 엔티티를
 * 알게 되어 간선이 되돌아온다.
 *
 * @param email 로그인 ID이기도 하다 (H1-2 — 바꾸면 다음 로그인부터 그 값이다)
 * @param phone 없을 수 있다 — 시드 계정은 전화번호를 담지 않는다
 */
public record AccountContact(String email, String phone) {
}
