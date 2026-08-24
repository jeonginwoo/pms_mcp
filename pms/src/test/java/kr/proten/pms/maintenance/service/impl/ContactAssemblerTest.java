package kr.proten.pms.maintenance.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.maintenance.service.dto.ContactCommand;
import kr.proten.pms.maintenance.service.entity.ContactDetails;
import kr.proten.pms.maintenance.service.entity.ContactParty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 수동 입력 연락처의 원문 조립 (AC D2-4 — 2026-08-24 사용자 결정).
 *
 * 시트 적재분은 원문을 파싱해 조각을 얻고({@link ContactParser}) 수동 입력은 조각을
 * 받아 원문을 만든다 — 방향이 반대다. 두 경로가 같은 표현을 내는지가 이 클래스의
 * 존재 이유이고, 그것을 {@code ContactParser}로 되돌려 확인한다.
 */
class ContactAssemblerTest {
    private final ContactAssembler assembler = new ContactAssembler();
    private final ContactParser parser = new ContactParser();

    @Test
    @DisplayName("조각을 시트와 같은 순서로 조립한다 — 이메일은 괄호 안")
    void assemblesInSheetOrder() {
        // Given
        ContactCommand command = new ContactCommand(ContactParty.CLIENT, "이준혁", "사원",
                "02-2140-5773", "wnsgur0718@kaoni.com");

        // When
        String raw = assembler.rawOf(command);

        // Then
        assertThat(raw).isEqualTo("이준혁 사원 02-2140-5773 (wnsgur0718@kaoni.com)");
    }

    @Test
    @DisplayName("조립본을 파서에 넣으면 같은 조각으로 돌아온다 — 두 경로의 표현이 갈리지 않는다")
    void assembledRawRoundTripsThroughTheParser() {
        // Given
        ContactCommand command = new ContactCommand(ContactParty.CONTRACTOR, "김승윤", "차장",
                "010-4849-7117", "ksy@sfzone.co.kr");

        // When
        ContactDetails parsed = parser.parse(assembler.rawOf(command));

        // Then
        assertThat(parsed).isEqualTo(new ContactDetails("김승윤", "차장", "010-4849-7117",
                "ksy@sfzone.co.kr"));
    }

    @Test
    @DisplayName("빈 조각은 자리를 차지하지 않는다 — 전화만 있는 시트 행과 같은 모양")
    void blankPartsAreOmitted() {
        // Given
        ContactCommand phoneOnly =
                new ContactCommand(ContactParty.CLIENT, null, null, "043-717-7822", null);

        // When
        String raw = assembler.rawOf(phoneOnly);

        // Then
        assertThat(raw).isEqualTo("043-717-7822");
    }

    @Test
    @DisplayName("조각이 하나도 없으면 400 — 아무 정보도 없는 연락처는 연락처가 아니다")
    void emptyContactIsRejected() {
        // Given
        ContactCommand empty = new ContactCommand(ContactParty.CLIENT, " ", null, null, null);

        // When · Then
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> assembler.rawOf(empty));
    }

    @Test
    @DisplayName("구분(계약사·고객사)은 필수다 — 표의 not null이 곧 규칙")
    void partyIsRequired() {
        // Given
        ContactCommand noParty = new ContactCommand(null, "이준혁", "사원", null, null);

        // When · Then
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> assembler.toContact(1L, noParty));
    }
}
