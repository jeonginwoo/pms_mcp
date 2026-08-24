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

    /**
     * 주어진 id의 표시용 참조 — <b>재직 여부를 가리지 않는다</b>(2026-08-24 규약 변경).
     * 부재 id만 결과에서 빠지고, 퇴사자는 {@code active=false}로 함께 나온다.
     *
     * <p>구 규약은 활성 인원만 내주었는데, 그러면 <b>퇴사자가 남긴 과거 사실이 이름을
     * 잃는다</b>: 배정은 종료돼도 행이 남고(AC B2-1) 감사 로그는 append-only라(G1-2)
     * 지워진 사람을 영원히 가리키는데, 이름을 못 받은 화면은 그 자리에 {@code #17}을
     * 그린다(2026-08-24 실측 — 배정 패널·감사 표 두 곳). 재직 여부가 <b>판정 근거</b>인
     * 자리는 {@link #existsActive}가 이미 따로 답하므로, 표시 이름을 주는 이 창구가
     * 활성 필터를 들 이유가 없다.
     */
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
