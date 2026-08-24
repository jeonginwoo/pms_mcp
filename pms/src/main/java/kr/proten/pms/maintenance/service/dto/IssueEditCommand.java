package kr.proten.pms.maintenance.service.dto;

import kr.proten.pms.maintenance.service.entity.IssueStatus;

/**
 * 이슈 처리 입력 (AC D3-2) — 상태 전이와 담당 재배정.
 *
 * <p><b>{@code null}은 "그대로 둔다"다</b>(PATCH 의미론 — §7). 상태만 바꾸는 요청과
 * 담당만 바꾸는 요청이 둘 다 흔하고, 안 보낸 칸을 기본값으로 덮으면 담당자를 바꾸려는
 * 요청이 상태를 되돌린다.
 *
 * <p>그래서 <b>담당 해제는 표현할 수 없다</b> — {@code null}이 이미 다른 뜻을 쓰고 있고,
 * AC에 해제 요구가 없어 별도 플래그를 만들지 않았다(저장소의 {@code unassignedOnly}가
 * 같은 이유로 별 파라미터인 것과 대칭이다: 그쪽은 <b>요구가 있었다</b>).
 *
 * <p>둘 다 {@code null}이면 아무것도 바꾸지 않는다 — 오류가 아니다. 감사에 행이 남지
 * 않는 것으로 충분히 표현된다(diff가 비면 기록하지 않는다).
 */
public record IssueEditCommand(IssueStatus status, Long assigneeId) {
}
