package kr.proten.pms.maintenance.service.dto;

import java.util.List;
import kr.proten.pms.person.PersonRef;

/**
 * 사이트 표현 — 담당 엔지니어를 id가 아니라 참조로 싣는다(이름을 되묻게 하지 않는다).
 * {@code engineer}가 null인 것은 미배정이라는 상태다(신규 예정·종료 섹션).
 */
public record SiteView(
        long id,
        String name,
        String channel,
        String serverSpec,
        PersonRef engineer,
        List<ContactView> contacts) {
}
