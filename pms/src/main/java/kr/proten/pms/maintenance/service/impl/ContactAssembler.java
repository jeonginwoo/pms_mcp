package kr.proten.pms.maintenance.service.impl;

import java.util.ArrayList;
import java.util.List;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.maintenance.service.dto.ContactCommand;
import kr.proten.pms.maintenance.service.entity.ContactDetails;
import kr.proten.pms.maintenance.service.entity.MaintenanceContact;
import org.springframework.stereotype.Component;

/**
 * 수동 입력 연락처의 원문을 만든다 — {@link ContactParser}의 반대 방향 (AC D2-4).
 *
 * 시트 적재분은 담당자 문자열이 원본이고 조각은 그것을 파싱한 결과인데, 화면 입력은
 * 조각이 원본이고 붙여넣을 원문이 없다. 그런데 {@code raw}는 not null이다 — 파싱이
 * 놓친 것의 유일한 출처라는 이유로 그렇게 정해져 있다(2026-08-23 결정).
 *
 * 그래서 조각을 <b>시트와 같은 순서로</b> 조립한다(2026-08-24 사용자 결정):
 * "이름 직급 전화 (이메일)". 이 모양을 고른 이유는 {@code ContactParser}에 다시 넣으면
 * 같은 조각이 나오기 때문이다 — 두 경로가 만든 원문이 화면에서 다르게 읽히지 않고,
 * 그 성질을 테스트가 왕복으로 잠근다.
 *
 * 대안(스키마를 nullable로 완화)을 택하지 않은 이유: 조회 화면이 조각이 빈 자리를
 * {@code raw}로 메우게 되어 있어(ContactView 주석) 원문이 비면 그 자리가 빈다.
 */
@Component
class ContactAssembler {
    // maintenance_contacts.raw 의 폭 — 넘치면 DB가 500으로 답하므로 여기서 400으로 돌린다
    private static final int RAW_LIMIT = 400;

    /** 입력 조각으로 저장할 연락처를 만든다 — 구분(계약사·고객사)은 필수다. */
    MaintenanceContact toContact(long siteId, ContactCommand command) {
        if (command.party() == null) {
            throw new ValidationException("연락처 구분은 필수입니다", "contacts.party");
        }

        ContactDetails details = new ContactDetails(trimmed(command.name()),
                trimmed(command.title()), trimmed(command.phone()), trimmed(command.email()));

        return MaintenanceContact.of(siteId, command.party(), details, rawOf(command));
    }

    /**
     * 조각을 시트와 같은 한 줄로 조립한다. 빈 조각은 자리를 차지하지 않는다 —
     * 전화만 있는 행이 시트에 실제로 있고, 빈칸을 남기면 파서가 그것까지 이름으로 읽는다.
     */
    String rawOf(ContactCommand command) {
        List<String> parts = new ArrayList<>();
        add(parts, trimmed(command.name()));
        add(parts, trimmed(command.title()));
        add(parts, trimmed(command.phone()));
        String email = trimmed(command.email());

        if (email != null) {
            parts.add("(" + email + ")");
        }

        if (parts.isEmpty()) {
            throw new ValidationException(
                    "연락처는 이름·직급·전화·이메일 중 하나 이상이 필요합니다", "contacts");
        }

        String raw = String.join(" ", parts);

        if (raw.length() > RAW_LIMIT) {
            throw new ValidationException(
                    "연락처가 " + RAW_LIMIT + "자를 넘습니다", "contacts");
        }

        return raw;
    }

    private static void add(List<String> parts, String value) {
        if (value != null) {
            parts.add(value);
        }
    }

    /** 빈 문자열과 null을 한 가지로 모은다 — 공백만 있는 입력은 값이 없는 것이다. */
    private static String trimmed(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
