package kr.proten.pms.project.service;

import kr.proten.pms.project.service.dto.ProgressUpdateResult;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.dto.UpdateProgressCommand;
import kr.proten.pms.project.service.entity.ProjectRole;

/**
 * 프로젝트 생애주기 — 진척·완료·재개·담당 교체 (AC A2 · A6-1 · A7).
 *
 * CRUD와 갈라 둔 축은 **§5 상태 머신**이다. 여기 있는 행위들은 값을 바꾸는 것이
 * 아니라 프로젝트가 어느 단계에 있는지를 바꾸거나(완료·재개) 그 단계에서만
 * 허용된다(진척률은 진행중에서만 — A2-9). PM 교체도 여기 있다: 상태는 아니지만
 * 프로젝트의 "누가 끌고 가는가"를 바꾸는 생애주기 사건이고, 판정·감사 순서가
 * 나머지와 같다.
 *
 * `/mcp`의 `update_progress`가 붙는 자리가 여기다 — 2단계 확인 프로토콜이
 * 서비스에 있으므로 웹과 챗이 같은 권한·감사·거절을 받는다(구조 원칙 5).
 */
public interface ProjectLifecycleService {

    /**
     * 진척률 2단계 갱신 (AC A2-1·A2-2).
     * confirmed=false는 요약만 돌려주고 DB를 건드리지 않는다.
     */
    ProgressUpdateResult updateProgress(long callerPersonId, UpdateProgressCommand command);

    /** 완료 처리 (AC A7-1) — 진행중·진척률 100%가 전제다. */
    ProjectDetail complete(long callerPersonId, long projectId, long version);

    /** 재개 (AC A7-3) — 완료 → 진행중, 진척률은 90으로 돌아간다. */
    ProjectDetail reopen(long callerPersonId, long projectId, long version);

    /** PM 교체 (AC A6-1) — 승격·강등·managerId 동기화가 한 트랜잭션이다. */
    ProjectDetail changeManager(
            long callerPersonId, long projectId, long personId, long version);

    /**
     * 역할 지정·교체 (AC A6-3·A6-6·A6-7) — PL·참여자만 이 경로로 바꾼다.
     *
     * PM은 {@link #changeManager}가 전담한다(A6-7 `422 INVALID_ROLE`): "프로젝트당 PM 1행"
     * 불변식(A6-5)은 승격과 직전 PM 강등을 한 트랜잭션에서 해야 성립하는데, 역할 하나만
     * 바꾸는 이 경로로는 그것을 보장할 수 없다.
     *
     * 해제는 `role=PARTICIPANT`다 — 배정 자체의 종료는 US-B2(`DELETE /assignments/{id}`)다.
     * 대상이 미배정이면 배정을 함께 만든다(A6-6 기본값: 프로젝트 기간 · M/M 0).
     */
    ProjectDetail changeRole(
            long callerPersonId, long projectId, long personId, ProjectRole role);
}
