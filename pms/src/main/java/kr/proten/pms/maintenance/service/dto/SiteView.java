package kr.proten.pms.maintenance.service.dto;

import java.util.List;
import kr.proten.pms.person.PersonRef;

/**
 * 사이트 표현 — 담당 엔지니어를 id가 아니라 참조로 싣는다(이름을 되묻게 하지 않는다).
 * {@code engineer}가 null인 것은 미배정이라는 상태다(신규 예정·종료 섹션).
 *
 * <p>{@code version}은 쓰기(D2-4)가 생기며 붙었다: {@code PUT /sites/{id}}가 version을
 * 요구하는데 사이트를 담는 응답이 셋(상세·목록·쓰기 응답) 다 그것을 싣지 않으면
 * 화면이 낙관적 락에 넣을 값을 어디서도 얻지 못한다.
 */
public record SiteView(
        long id,
        String name,
        String channel,
        String serverSpec,
        PersonRef engineer,
        List<ContactView> contacts,
        long version) {
}
