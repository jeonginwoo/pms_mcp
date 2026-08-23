package kr.proten.pms.maintenance.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import kr.proten.pms.maintenance.service.entity.ContactDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 연락처 파싱 단위 테스트 — 시드에 실제로 있는 네 가지 형태를 그대로 쓴다.
 *
 * 이 파서의 계약은 "완전 파싱"이 아니라 <b>확신 없으면 비운다</b>다(2026-08-23 결정):
 * 원문은 raw에 남으므로 못 뽑은 것은 사라지지 않지만, 틀리게 뽑은 것은 화면이
 * 거짓을 말하게 한다.
 */
class ContactParserTest {
    private final ContactParser parser = new ContactParser();

    @Test
    @DisplayName("이름·직급·전화·이메일이 다 있는 형태")
    void parsesFullForm() {
        ContactDetails details = parser.parse("이준혁 사원 02-2140-5773 (wnsgur0718@kaoni.com)");

        assertThat(details.name()).isEqualTo("이준혁");
        assertThat(details.title()).isEqualTo("사원");
        assertThat(details.phone()).isEqualTo("02-2140-5773");
        assertThat(details.email()).isEqualTo("wnsgur0718@kaoni.com");
    }

    @Test
    @DisplayName("전화만 있는 형태 — 이름을 추측하지 않는다")
    void phoneOnlyLeavesNameEmpty() {
        ContactDetails details = parser.parse("043-717-7822");

        assertThat(details.phone()).isEqualTo("043-717-7822");
        assertThat(details.name()).isNull();
        assertThat(details.title()).isNull();
    }

    @Test
    @DisplayName("회사명이 앞에 붙은 형태 — 직급 바로 앞 토큰이 이름이다")
    void companyPrefixDoesNotBecomeName() {
        ContactDetails details = parser.parse("에스에프존 김승윤 차장 010-4849-7117 (ksy@sf-zone.com)");

        // 첫 한글 토큰을 집으면 "에스에프존"이 이름이 된다
        assertThat(details.name()).isEqualTo("김승윤");
        assertThat(details.title()).isEqualTo("차장");
    }

    @Test
    @DisplayName("이름에 괄호 주석이 붙은 형태 — 주석은 이름의 일부가 아니다")
    void parenthesisNoteIsStripped() {
        ContactDetails details =
                parser.parse("에스에프존 이희진(원격) 사원 010-8892-9749 (leehj@sf-zone.com)");

        assertThat(details.name()).isEqualTo("이희진");
        assertThat(details.title()).isEqualTo("사원");
        assertThat(details.email()).isEqualTo("leehj@sf-zone.com");
    }

    @Test
    @DisplayName("빈 값·null은 빈 조각으로 — 예외를 던지지 않는다")
    void blankIsEmpty() {
        assertThat(parser.parse(null)).isEqualTo(ContactDetails.empty());
        assertThat(parser.parse("  ")).isEqualTo(ContactDetails.empty());
    }
}
