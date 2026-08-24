package kr.proten.pms.maintenance.controller.dto;

import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.maintenance.service.dto.IssueEditCommand;
import kr.proten.pms.maintenance.service.entity.IssueStatus;

/**
 * 이슈 처리 요청 (AC D3-2) — 상태 전이·담당 재배정.
 *
 * <p>{@code status}·{@code assigneeId}에 {@code @NotNull}이 없는 것이 PATCH의 의미다:
 * 안 보낸 칸은 그대로 둔다({@code IssueEditCommand} 주석). 그래서 <b>둘 다 비어도
 * 400이 아니다</b> — 아무것도 바꾸지 않는 요청은 아무것도 바꾸지 않는다.
 *
 * @param version 필수다 — 계약·사이트와 같은 이유로 애너테이션이 아니라
 *                {@link #requiredVersion()}이 본다
 */
public record IssueEditRequest(IssueStatus status, Long assigneeId, Long version) {

    public IssueEditCommand toCommand() {
        return new IssueEditCommand(status, assigneeId);
    }

    /** 없으면 낙관적 락이 조용히 통과해 마지막 쓰기가 이긴다 (AC D3-2). */
    public long requiredVersion() {
        if (version == null) {
            throw new ValidationException("version은 필수입니다", "version");
        }

        return version;
    }
}
