package kr.proten.pms.project.service;

/**
 * 프로젝트 삭제 유스케이스 — AC A4-1·A4-2.
 *
 * 삭제는 soft 삭제다 — 배정·감사 로그가 이 프로젝트를 가리키고 있어 행을 지울 수 없다.
 * 권한은 **PM 또는 "프로젝트 생성" 플래그 보유자**다 (2026-08-22 결정 — 상위 PRD §4-2의
 * "삭제=PM 전용 고정 행"을 확장했다. 만든 사람이 지울 수 있어야 한다는 실무 요구).
 */
public interface ProjectDeleteService {

    /** 프로젝트를 soft 삭제한다 — 목록·중복 검사에서 빠진다. */
    void delete(long callerPersonId, long projectId);
}
