package kr.proten.pms.project.service;

import kr.proten.pms.project.service.dto.CreateProjectCommand;
import kr.proten.pms.project.service.dto.EditProjectCommand;
import kr.proten.pms.project.service.dto.ProjectDetail;

/**
 * 프로젝트 CRUD — AC A1 · A4 · A5.
 *
 * 상태 전이는 여기 없다 — 완료·재개·진척률·PM 교체는 §5 상태 머신을 따르는 별개
 * 계약(`ProjectLifecycleService`)이다. `edit`이 상태를 **한 칸 앞으로만** 옮길 수
 * 있는 것이 그 경계다(A5-2 — 역방향은 전용 경로로도 불가).
 *
 * 판정 축이 셋 다 다르다: 생성은 프로젝트가 아직 없어 권한 그룹의 "프로젝트 생성"
 * 플래그, 수정은 프로젝트 역할 PM·PL, 삭제는 PM 또는 "프로젝트 생성" 플래그
 * (2026-08-22 결정). 그래도 한 계약인 이유는 대상이 같은 애그리거트이기 때문이다.
 */
public interface ProjectCommandService {

    /** 프로젝트를 만들고 지정 역할로 배정한다 (AC A1-1). */
    ProjectDetail create(long callerPersonId, CreateProjectCommand command);

    /** 정보 수정 + 순방향 한 칸 전이 (AC A5-1~A5-3). */
    ProjectDetail edit(long callerPersonId, EditProjectCommand command);

    /** 소프트 삭제 (AC A4-1) — 과거 배정·감사가 그 프로젝트를 가리킨다. */
    void delete(long callerPersonId, long projectId);
}
