package kr.proten.pms.project.service;

import kr.proten.pms.project.service.dto.AssignmentView;
import kr.proten.pms.project.service.dto.CreateAssignmentCommand;
import kr.proten.pms.project.service.dto.UpdateAssignmentCommand;

/**
 * 인력 배정 유스케이스 — AC B1-1·B1-2·B1-4·B2-1.
 *
 * 배정은 PM의 일이다(상위 PRD §4-2 `ASSIGN`) — M/M 입력이 계획의 근거가 되므로
 * 참여자·PL이 스스로 바꿀 수 없다. 종료는 행을 지우지 않는다: 지난 달 가동률은
 * 그때의 배정으로 계산되어야 한다.
 */
public interface AssignmentService {

    /** 인력을 배정한다 — 종료되지 않은 같은 인원의 배정이 있으면 409다. */
    AssignmentView assign(long callerPersonId, CreateAssignmentCommand command);

    /** 배정 기간·투입 M/M을 수정한다. */
    AssignmentView update(long callerPersonId, UpdateAssignmentCommand command);

    /** 배정을 종료한다 — 종료월 이후 가동률에서 빠진다. */
    void close(long callerPersonId, long assignmentId);
}
