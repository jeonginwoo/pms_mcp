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
 * <p><b>삭제·수정이 없는 것은 누락이 아니다</b>: 이슈 삭제는 AC에 없고(상태 {@code 완료}가
 * 종결이다), 코멘트는 append-only라 보정도 새 코멘트로만 한다(D3-3 — 구
 * {@code MaintenanceLog} 불변식 계승).
 *
 * <p>{@code service/}에 있는 것은 {@link ContractCommandService}와 같은 이유다 — 밖에서
 * 부르는 모듈이 없다. 유지보수 쓰기는 웹 화면만의 입구다(구조 원칙 5 — 쓰기 도구는
 * {@code update_progress} 하나).
 */
public interface IssueCommandService {

    /**
     * 이슈를 등록한다 (AC D3-1) — 담당자는 사이트의 담당 엔지니어이고,
     * 커밋 후 {@code MaintenanceIssueRegistered}가 발행된다.
     */
    IssueView register(long callerPersonId, IssueCommand command);

    /**
     * 이슈를 처리한다 (AC D3-2) — 상태 전이·담당 재배정.
     * {@code version}이 어긋나면 409 STALE_VERSION, 전이가 허용되지 않으면
     * 409 INVALID_TRANSITION이다.
     */
    IssueView process(
            long callerPersonId, long issueId, IssueEditCommand command, long version);

    /**
     * 코멘트를 붙인다 (AC D3-3) — <b>append-only</b>다.
     * {@code version}을 받지 않는다: 코멘트를 더하는 것은 이슈를 고치는 것이 아니라
     * 이슈에 사실을 쌓는 것이고, 두 사람이 동시에 써도 둘 다 남아야 맞다.
     */
    CommentView addComment(long callerPersonId, long issueId, String content);
}
