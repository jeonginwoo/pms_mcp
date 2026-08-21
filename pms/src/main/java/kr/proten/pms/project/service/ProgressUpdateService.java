package kr.proten.pms.project.service;

import kr.proten.pms.project.service.dto.ProgressUpdateResult;
import kr.proten.pms.project.service.dto.UpdateProgressCommand;

/**
 * 진척률 갱신 유스케이스 — AC A2-1~A2-8.
 * 2단계 확인이 이 서비스의 프로토콜이다: confirmed=false는 변경 요약만 돌려주고
 * DB를 건드리지 않으며, confirmed=true에서 낙관적 락을 검사하고 커밋한다.
 * 웹 UI와 MCP `update_progress`가 같은 서비스를 쓴다(PRD-pms US-A2).
 */
public interface ProgressUpdateService {

    /** 진척률을 확인 후 갱신한다. */
    ProgressUpdateResult update(long callerPersonId, UpdateProgressCommand command);
}
