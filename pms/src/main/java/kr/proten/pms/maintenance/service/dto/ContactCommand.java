package kr.proten.pms.maintenance.service.dto;

import kr.proten.pms.maintenance.service.entity.ContactParty;

/**
 * 사이트 연락처 입력 (AC D2-3·D2-4) — 사이트 요청에 동봉된다.
 *
 * 시트 적재분과 달리 원문이 없다: 수동 입력에는 붙여넣을 담당자 문자열이 없고
 * 조각만 있다. 그래서 {@code raw}를 받지 않고 조립한다({@code ContactAssembler}) —
 * 조립본은 {@code ContactParser}가 다시 같은 조각으로 되돌릴 수 있는 모양이라
 * 적재분과 표현이 갈리지 않는다(2026-08-24 사용자 결정).
 */
public record ContactCommand(
        ContactParty party, String name, String title, String phone, String email) {
}
