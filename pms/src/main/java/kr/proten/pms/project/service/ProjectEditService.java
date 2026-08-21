package kr.proten.pms.project.service;

import kr.proten.pms.project.service.dto.EditProjectCommand;
import kr.proten.pms.project.service.dto.ProjectDetail;

/**
 * 프로젝트 정보·상태 수정 유스케이스 — AC A5-1~A5-3.
 *
 * 상태를 서버가 강제하는 자리다: 수정 폼이 status를 자유 편집하게 두면 완료·이관
 * 권한을 정보 수정 권한으로 우회할 수 있다(2026-08-02 결정). 그래서 이 경로는
 * 순방향 한 칸만 허용하고, 완료·재개·이관은 전용 경로만의 몫이다(§5).
 */
public interface ProjectEditService {

    /** 정보를 수정하고, 요청된 상태가 다르면 순방향으로 한 칸 전이한다. */
    ProjectDetail edit(long callerPersonId, EditProjectCommand command);
}
