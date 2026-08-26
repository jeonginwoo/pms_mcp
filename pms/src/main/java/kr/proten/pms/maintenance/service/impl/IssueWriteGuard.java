package kr.proten.pms.maintenance.service.impl;

import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.maintenance.service.entity.MaintenanceIssue;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import org.springframework.stereotype.Component;

/**
 * 이슈 정정·삭제의 관문 (AC D3-5·D3-6 — 2026-08-26 사용자 결정).
 *
 * <p><b>등록·처리·코멘트에는 관문이 없다</b>(US-D3 = 로그인 사용자 전체). 정정과 삭제만
 * 좁히는 이유는 그 둘이 <b>남이 쓴 것을 지우거나 바꾸는 행위</b>라는 점이다 — 이슈
 * 게시판은 전사 공개라(D4-3) 관문이 없으면 누구나 남의 이슈를 지울 수 있다.
 *
 * <p>통과 조건 셋: <b>등록자 · 현재 담당자 · "계약 관리" 플래그 보유자</b>. 셋을 OR로
 * 둔 이유는 각각이 다른 공백을 메우기 때문이다 — 등록자는 자기 오타를 고쳐야 하고
 * (그 공백이 이 작업의 착수 계기다), 담당자는 처리 과정에서 내용을 다듬어야 하고,
 * <b>시드 이슈 267건은 등록자가 null</b>이라(구 게시판이 작성자를 남기지 않았다)
 * 관리 플래그 없이는 아무도 손댈 수 없다.
 *
 * <p>{@code ContractWriteGuard}를 재사용하지 않고 따로 두는 것은 판정이 다르기 때문이다:
 * 그쪽은 플래그 <b>하나</b>를 요구하는 관문이고 여기서 플래그는 <b>세 갈래 중 하나</b>다.
 * 그 클래스에 이슈 갈래를 넣으면 "계약 쓰기는 플래그가 필수"라는 사실이 흐려진다.
 */
@Component
class IssueWriteGuard {
    private final OrgPermissionService orgPermissionService;

    IssueWriteGuard(OrgPermissionService orgPermissionService) {
        this.orgPermissionService = orgPermissionService;
    }

    /** 정정·삭제 권한이 없으면 403. */
    void require(long callerPersonId, MaintenanceIssue issue) {
        if (isReporter(callerPersonId, issue)
                || isAssignee(callerPersonId, issue)
                || orgPermissionService.has(callerPersonId, OrgPermission.MANAGE_CONTRACTS)) {
            return;
        }

        throw new ForbiddenException("이슈를 수정·삭제할 권한이 없습니다 — 등록자·담당자이거나 계약 관리 권한이 필요합니다");
    }

    private boolean isReporter(long callerPersonId, MaintenanceIssue issue) {
        return issue.getReporterId() != null && issue.getReporterId() == callerPersonId;
    }

    private boolean isAssignee(long callerPersonId, MaintenanceIssue issue) {
        return issue.getAssigneeId() != null && issue.getAssigneeId() == callerPersonId;
    }
}
