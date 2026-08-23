package kr.proten.pms.project;

import java.util.List;

/**
 * 프로젝트 조회 — 모듈 밖(현재는 `/mcp` 어댑터)에 여는 계약 (2026-08-23 신설).
 *
 * <p>내부 {@code ProjectQueryService}를 그대로 올리지 않는 이유는 {@code PersonLookupService}
 * 선례와 같다: 내부 계약은 웹의 page 봉투와 감사 이력까지 갖는데 어댑터는 목록 절단만
 * 약속했고, 응답에 <b>팀·부문</b>을 실어야 한다(MCP {@code ProjectSummary}).
 *
 * <p><b>가시성은 판정한다</b>: 유지보수와 달리 프로젝트는 범위 밖이 404로 숨는다
 * (AC A3-2). 그래서 호출자 id를 받는다 — 챗에서 보이는 것 = 화면에서 보이는 것.
 *
 * <p>상태를 <b>한국어 라벨</b>로 받는다(도구 파라미터가 그 형태다 —
 * `"계약대기/수주확정/진행중/완료"`). 모르는 라벨은 빈 결과가 아니라 예외다:
 * 오타를 조용히 "필터 없음"으로 바꾸면 사용자가 틀린 답을 받는다.
 */
public interface ProjectLookupService {
    /**
     * 프로젝트 검색 (MCP {@code search_projects} 목록 갈래 · AC A3-1).
     * 키워드는 이름·고객사·솔루션 부분 일치.
     */
    List<ProjectBrief> search(long callerPersonId, String statusLabel, String keyword, int limit);

    /** 프로젝트 상세 (같은 도구의 projectId 갈래). 가시성 밖·부재는 같은 404다(A3-2). */
    ProjectDetailBrief detail(long callerPersonId, long projectId);
}
