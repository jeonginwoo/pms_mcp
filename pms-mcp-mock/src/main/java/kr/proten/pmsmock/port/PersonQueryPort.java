package kr.proten.pmsmock.port;

import java.util.List;

/**
 * 사람 검색 포트 — 실전 PMS의 identity 모듈 application 서비스가 구현할 계약.
 * 목업(B-0)에는 가시성 개념이 없지만, 실전에서는 요청자 기준 가시성 필터가 이 계약 뒤에 선다.
 */
public interface PersonQueryPort {
    /**
     * 이름(부분 일치) 또는 팀명(정확 일치)으로 사람을 찾습니다. 둘 다 생략하면 전체를 반환합니다.
     */
    List<PersonSummaryDto> findVisible(String name, String team);
}
