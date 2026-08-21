package kr.proten.pms.project.service;

import kr.proten.pms.project.service.dto.ProjectDetail;

/**
 * 프로젝트 역할 지정·교체 유스케이스 — AC A6-1·A6-2·A6-4·A6-5.
 *
 * PM 교체가 전용 경로인 이유: 프로젝트당 `role=PM` 정확히 1행이라는 불변식(A6-5)은
 * 새 PM 승격과 직전 PM 강등이 **한 트랜잭션**에서 함께 일어나야 지켜진다. 정보 수정
 * (US-A5)이나 배정 수정(US-B1)으로 role을 건드릴 수 없게 막아 둔 것도 같은 이유다.
 * PL·참여자 역할 지정(A6-3)은 아직 범위 밖이다.
 */
public interface ProjectRoleService {

    /** 대표 PM을 교체한다 — 대상이 미배정이면 배정을 함께 만든다 (AC A6-4). */
    ProjectDetail changeManager(long callerPersonId, long projectId, long personId, long version);
}
