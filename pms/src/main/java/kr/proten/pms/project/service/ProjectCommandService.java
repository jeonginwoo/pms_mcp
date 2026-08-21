package kr.proten.pms.project.service;

import kr.proten.pms.project.service.dto.CreateProjectCommand;
import kr.proten.pms.project.service.dto.ProjectDetail;

/**
 * 프로젝트 생성 유스케이스 — AC A1-1~A1-6.
 * 생성은 프로젝트가 아직 없는 행위라 프로젝트 역할이 판정 축이 될 수 없다 —
 * 권한 그룹의 "프로젝트 생성" 플래그가 유일한 판정자다(상위 PRD §4-3).
 * 정보 수정(US-A5)은 프로젝트 역할이 판정하므로 계약을 따로 둔다.
 */
public interface ProjectCommandService {

    /** 프로젝트를 만들고 지정 역할로 배정한다. */
    ProjectDetail create(long callerPersonId, CreateProjectCommand command);
}
