package kr.proten.pms.maintenance.service;

import kr.proten.pms.maintenance.service.dto.CommentView;
import kr.proten.pms.maintenance.service.dto.IssueCommand;
import kr.proten.pms.maintenance.service.dto.IssueEditCommand;
import kr.proten.pms.maintenance.service.dto.IssueView;

/**
 * 유지보수 이슈 쓰기 (US-D3).
 *
 * <p>조회({@link IssueQueryService})·계약 쓰기({@link ContractCommandService})와 계약을
 * 셋으로 나눈 이유는 <b>판정 축이 셋이라는 것</b>이다(conventions §5). 조회는 전사
 * 공개라 화자를 안 받고(D4-3), 계약 쓰기는 "계약 관리" 플래그가 판정하고(D2-3),
 * <b>이슈 쓰기는 로그인 사용자 전체다</b>(US-D3 대괄호). 셋을 한 인터페이스에 합치면
 * 어느 메서드가 어떤 관문을 지나는지 읽는 사람이 매번 확인해야 한다.
 *
 * <p><b>{@code ContractWriteGuard}를 재사용하지 않는다</b> — 이슈에 그 관문을 걸면
 * 팀원이 자기가 처리할 이슈를 등록할 수 없다. 유지보수 이슈는 계약과 달리 현장에서
 * 올라오는 것이고, AC가 그 차이를 대괄호로 적어 뒀다.
 *
 * <p><b>정정·삭제는 관문이 다르다</b>(2026-08-26 — AC D3-5·D3-6·D3-7 신설, 사용자 결정).
 * 등록·처리·코멘트 추가는 여전히 전원이지만, <b>남이 쓴 것을 바꾸거나 지우는 행위</b>는
 * {@code IssueWriteGuard}(등록자·담당자·"계약 관리" 플래그)와 작성자 판정이 든다.
 * 게시판이 전사 공개라(D4-3) 관문이 없으면 누구나 남의 이슈를 지울 수 있다.
 *
 * <p>구 주석은 <i>"삭제·수정이 없는 것은 누락이 아니다"</i>였고 그 근거는 "AC에 없다"와
 * "코멘트는 append-only(D3-3)"였다. <b>둘 다 사용자 결정으로 뒤집혔다</b> — 착수 계기는
 * 실측된 공백이다: 등록 경로는 있는데 <b>제목 오타를 고칠 방법이 아예 없었다</b>.
 *
 * <p>{@code service/}에 있는 것은 {@link ContractCommandService}와 같은 이유다 — 밖에서
 * 부르는 모듈이 없다. 유지보수 쓰기는 웹 화면만의 입구다(구조 원칙 5 — 쓰기 도구는
 * {@code update_progress} 하나).
 */
public interface IssueCommandService {

    /**
     * 이슈를 등록한다 (AC D3-1) — 담당자는 사이트의 담당 엔지니어이고,
     * 커밋 후 {@code MaintenanceIssueRegistered}가 발행된다.
     * 등록자({@code reporterId})는 화자다 — 정정 권한이 그 값을 본다.
     */
    IssueView register(long callerPersonId, IssueCommand command);

    /**
     * 이슈를 처리·정정한다 (AC D3-2 상태·담당 + AC D3-5 제목·유형·본문).
     *
     * <p>{@code version}이 어긋나면 409 STALE_VERSION, 전이가 허용되지 않으면
     * 409 INVALID_TRANSITION이다. <b>정정 칸(제목·유형·본문)이 실려 있을 때만</b>
     * 403 관문을 지난다 — 상태 전이·재배정은 그대로 전원이다.
     */
    IssueView process(
            long callerPersonId, long issueId, IssueEditCommand command, long version);

    /**
     * 이슈를 삭제한다 (AC D3-6) — <b>soft 삭제</b>이고 조회에서 빠진다.
     * 등록자·담당자·"계약 관리" 플래그만 할 수 있고(403), {@code version}이 어긋나면 409다.
     */
    void delete(long callerPersonId, long issueId, long version);

    /**
     * 코멘트를 붙인다 (AC D3-3) — 로그인 사용자 전체.
     * {@code version}을 받지 않는다: 코멘트를 더하는 것은 이슈를 고치는 것이 아니라
     * 이슈에 사실을 쌓는 것이고, 두 사람이 동시에 써도 둘 다 남아야 맞다.
     */
    CommentView addComment(long callerPersonId, long issueId, String content);

    /**
     * 코멘트를 고친다 (AC D3-7) — <b>작성자 본인만</b>(403). 고쳐진 사실은
     * {@code updatedAt}으로 남는다.
     */
    CommentView editComment(long callerPersonId, long commentId, String content);

    /** 코멘트를 지운다 (AC D3-7) — <b>작성자 본인만</b>(403). 행을 지운다. */
    void deleteComment(long callerPersonId, long commentId);
}
