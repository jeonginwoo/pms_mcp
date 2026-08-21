package kr.proten.pms.project.service;

import kr.proten.pms.project.service.dto.ProjectDetail;

/**
 * 완료 처리·재개 유스케이스 — AC A7-1~A7-5.
 *
 * 두 행위를 한 계약에 두는 이유: 권한상 한 토글이다(§4 `COMPLETE_REOPEN` — 완료를
 * 할 수 있는 사람이 되돌릴 수도 있어야 사고를 수습할 수 있다). 그래서 배정 전원에게
 * 열려 있고, 누가 되돌렸는지는 감사 로그가 답한다(US-G2 신설 근거).
 */
public interface ProjectCompletionService {

    /** 완료 처리한다 — 진행중·진척률 100%가 전제다 (AC A7-1·A7-2). */
    ProjectDetail complete(long callerPersonId, long projectId, long version);

    /** 재개한다 — 완료 상태에서만 가능하고 진척률은 90으로 돌아간다 (AC A7-3). */
    ProjectDetail reopen(long callerPersonId, long projectId, long version);
}
