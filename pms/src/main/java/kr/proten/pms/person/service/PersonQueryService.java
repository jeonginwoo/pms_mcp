package kr.proten.pms.person.service;

import java.util.List;
import kr.proten.pms.person.service.dto.PersonRef;

/**
 * 인력 조회 유스케이스 — 가시성 필터와 404 은닉이 이 계층에 있다 (구조 원칙 3).
 * 목록은 가시성 내 부분집합만 돌려주고, 단건은 부재·시스템 계정·비활성·가시성
 * 밖을 같은 404로 수렴시킨다 (상위 PRD §4-4).
 */
public interface PersonQueryService {

    /** 가시성 범위 내 인원 목록 — 시스템 계정·비활성 인원은 제외한다. */
    List<PersonRef> listVisible(long callerPersonId);

    /** 인원 단건 조회 — 노출 대상이 아닌 인원과 가시성 밖 인원은 같은 404다. */
    PersonRef getPerson(long callerPersonId, long personId);
}
