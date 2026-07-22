package kr.proten.pmsmock.port;

import java.util.List;

/**
 * 프로젝트 검색 포트 — 실전 PMS의 project 모듈 application 서비스가 구현할 계약.
 * 실전에서는 가시성 선필터와 404 은닉이 이 계약 뒤에 선다(범위 밖 ID도 "없음"과 동일하게 빈 결과).
 */
public interface ProjectQueryPort {
    /**
     * 상태·키워드로 프로젝트를 검색합니다. projectId를 지정하면 배정이 포함된 상세 1건을 반환합니다.
     * 키워드는 프로젝트명과 고객사에 부분 일치합니다.
     */
    List<ProjectDto> searchVisible(String status, String keyword, Long projectId);
}
