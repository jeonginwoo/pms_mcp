package kr.proten.pms.person.service;

import kr.proten.pms.person.service.dto.MeView;

/**
 * 내 계정 조회 (PRD-pms §7 `GET /api/me`).
 * 화자 자신은 언제나 조회 대상이다 — 가시성 scope가 SELF여도 본인은 포함된다.
 */
public interface MeQueryService {

    /** 화자의 신원과 권한 그룹 플래그. */
    MeView me(long callerPersonId);
}
