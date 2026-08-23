package kr.proten.pms.person;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 인원 참조 조회 — 다른 모듈이 인원 id의 유효성을 확인하고 표시 이름을 얻는 경로.
 * 모듈 간 연결은 id로만 한다는 규칙(PRD-pms §0)의 질의 쪽 창구이며, 가시성 판정은
 * 하지 않는다: 참조 검증(존재하는 인원인가)과 가시성(내가 볼 수 있는가)은 다른
 * 질문이라 호출 측 유스케이스가 자기 맥락에서 판정한다.
 */
public interface PersonDirectoryService {

    /** 활성 인원으로 존재하는가 — 참조 검증(예: AC A1-3 REF_NOT_FOUND)에 쓴다. */
    boolean existsActive(long personId);

    /** 주어진 id 중 활성 인원만 참조 값으로. 부재 id는 결과에서 빠진다. */
    List<PersonRef> findRefs(Collection<Long> personIds);

    /**
     * 이름으로 인원 id를 찾는다 — 시드 원본이 사람을 이름으로 적어 둔 경우의 창구다
     * (유지보수 계약의 영업대표 3명, 2026-08-23 신설).
     *
     * <p>운영 입력 경로에는 쓰지 않는다: 동명이인이 생기면 이름은 식별자가 아니게
     * 되고 그때 조용히 틀린 사람을 가리킨다. 그래서 **정확히 한 명일 때만** 답하고
     * 없거나 둘 이상이면 빈 값이다 — 호출자가 그 사실을 보고 판단하게 한다.
     */
    Optional<Long> findIdByExactName(String name);
}
