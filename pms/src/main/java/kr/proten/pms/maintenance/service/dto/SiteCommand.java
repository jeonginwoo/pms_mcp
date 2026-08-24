package kr.proten.pms.maintenance.service.dto;

import java.util.List;
import kr.proten.pms.maintenance.service.entity.SiteChannel;

/**
 * 사이트 등록·수정 입력 (AC D2-4).
 *
 * 연락처를 목록으로 동봉한다 — 연락처는 사이트에 붙는 것이고({@code ContractDetail}
 * 주석과 같은 이유) 사이트와 별개 라우트로 가르면 "사이트는 만들었는데 연락처를
 * 못 넣은" 중간 상태가 생긴다.
 *
 * 수정에서 이 목록은 <b>전체 교체</b>다(§7 PUT 의미론 — 알림 설정과 같은 규칙):
 * 보내지 않은 연락처를 "그대로 둔다"로 읽으면 화면에서 지운 연락처가 서버에 남아
 * 두 쪽이 갈린다. 빈 목록은 "연락처 없음"이다.
 *
 * @param engineerId 담당 엔지니어 — 미배정(null)은 정상 상태다(신규 예정·종료 사이트)
 */
public record SiteCommand(
        String name,
        SiteChannel channel,
        String serverSpec,
        Long engineerId,
        List<ContactCommand> contacts) {

    /** 연락처를 보내지 않은 것과 빈 목록은 같은 뜻이다 — 호출자마다 null을 다루지 않게 한다. */
    public SiteCommand {
        contacts = contacts == null ? List.of() : List.copyOf(contacts);
    }
}
