package kr.proten.pms.maintenance.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.proten.pms.maintenance.service.dto.ContactCommand;
import kr.proten.pms.maintenance.service.entity.ContactParty;

/**
 * 사이트 연락처 요청 (AC D2-4) — 사이트 요청에 동봉된다.
 *
 * 조각 넷은 전부 선택이다: 시트에는 전화만 있는 행이 실제로 있다. 넷이 모두 비면
 * 서비스가 400으로 거절한다({@code ContactAssembler}) — "하나 이상"은 애너테이션
 * 하나로 표현할 수 없는 조건이라 선언적 검증에 두지 않았다.
 *
 * 원문({@code raw})은 받지 않는다 — 조각으로 조립하는 것이 이 경로의 규칙이다.
 */
public record ContactRequest(
        @NotNull(message = "연락처 구분은 필수입니다") ContactParty party,
        @Size(max = 100, message = "이름은 100자를 넘을 수 없습니다") String name,
        @Size(max = 60, message = "직급은 60자를 넘을 수 없습니다") String title,
        @Size(max = 60, message = "전화는 60자를 넘을 수 없습니다") String phone,
        @Size(max = 200, message = "이메일은 200자를 넘을 수 없습니다") String email) {

    public ContactCommand toCommand() {
        return new ContactCommand(party, name, title, phone, email);
    }
}
