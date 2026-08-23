package kr.proten.pms.person;

import java.util.Collection;
import java.util.List;
import kr.proten.pms.person.PersonRef;

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
}
