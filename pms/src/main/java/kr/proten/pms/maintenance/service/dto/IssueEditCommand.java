package kr.proten.pms.maintenance.service.dto;

import kr.proten.pms.maintenance.service.entity.IssueStatus;
import kr.proten.pms.maintenance.service.entity.IssueType;

/**
 * 이슈 수정 — 상태·담당(AC D3-2) + <b>제목·유형·본문</b>(AC D3-5, 2026-08-26 신설).
 *
 * <p><b>null은 어디서나 "그대로"다.</b> 상태·담당이 이미 그 규약이었고 세 칸을 더할 때도
 * 지켰다 — 한 요청 안에서 규약이 갈리면 호출자가 칸마다 다른 규칙을 외워야 한다.
 * 그래서 <b>본문을 지우는 것은 빈 문자열</b>이다(엔티티가 null로 바꿔 저장한다).
 *
 * <p>{@code siteId}가 없는 것은 의도다: 이슈가 어느 계약에 속하는지가 사이트에서
 * 파생되므로 그것을 바꾸는 것은 정정이 아니라 이동이고, AC에 요구가 없다.
 */
public record IssueEditCommand(
        IssueStatus status,
        Long assigneeId,
        IssueType type,
        String title,
        String content) {

    /** 상태·담당만 바꾸는 요청 — D3-2 시절의 두 칸 호출부(테스트 포함)를 위한 좁은 문. */
    public static IssueEditCommand ofProcess(IssueStatus status, Long assigneeId) {
        return new IssueEditCommand(status, assigneeId, null, null, null);
    }

    /** 무엇이든 바꾸려 하는가 — 다섯 칸이 전부 null인 요청은 400이다(변경 없음). */
    public boolean isEmpty() {
        return status == null && assigneeId == null && type == null
                && title == null && content == null;
    }
}
