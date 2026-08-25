package kr.proten.pms.person.service.dto;

/**
 * 내 계정 상세 (AC H1-1 `GET /api/me/account`) — 내 계정 모달의 원천.
 *
 * <p><b>{@code GET /api/me}와 나뉜 이유</b>: 그쪽은 화면이 버튼을 감추려고 부팅 때
 * 읽는 것이라 권한 플래그가 실려 있고 모든 화면이 의존한다. 여기는 <b>수정 폼을
 * 되채우는 값</b>(H1-2)이라 연락처가 필요하고, 그것을 `/api/me`에 얹으면 부팅
 * 응답이 계정 정보를 늘 나르게 된다.
 *
 * <p><b>알림 설정은 담지 않는다</b>: §7이 `GET /api/me/notif-prefs`를 따로 뒀고(H1-4)
 * 화면도 그 라우트를 이미 쓴다. 여기 얹으면 같은 값의 원천이 둘이 된다.
 *
 * @param email 로그인 ID이기도 하다 · {@code phone}은 없을 수 있다(시드 계정)
 */
public record AccountView(Long id, String name, String email, String phone) {
}
