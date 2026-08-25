package kr.proten.pms.person.service.dto;

/**
 * 내 프로필 수정 입력 (AC H1-2 `PUT /api/me/profile`).
 *
 * <p><b>version이 없다</b>: 내 프로필을 동시에 두 곳에서 고치는 상황은 "다른 사람이
 * 이미 바꿨다"가 아니라 내가 두 탭을 연 것이다. 낙관적 락은 <b>남의 쓰기</b>로부터
 * 지키는 장치이고(§7의 다른 수정 경로가 그래서 version을 받는다), 여기에는 그 남이
 * 없다 — AC H1-2도 version을 적지 않았다.
 *
 * <p>{@code phone}은 비울 수 있다 — 없는 것이 정상 상태다(시드 계정).
 */
public record UpdateProfileCommand(String name, String email, String phone) {
}
