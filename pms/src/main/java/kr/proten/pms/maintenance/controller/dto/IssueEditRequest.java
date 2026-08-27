package kr.proten.pms.maintenance.controller.dto;

import jakarta.validation.constraints.Size;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.maintenance.service.dto.IssueEditCommand;
import kr.proten.pms.maintenance.service.entity.IssueStatus;
import kr.proten.pms.maintenance.service.entity.IssueType;

/**
 * 이슈 수정 요청 — 상태·담당(AC D3-2) + 제목·유형·본문(AC D3-5, 2026-08-26).
 *
 * <p>{@code PATCH}인 이유가 다섯 칸이 되며 더 분명해졌다: <b>칸들이 서로 독립</b>이라
 * 상태만 바꾸는 요청에 제목을 함께 실으라고 요구할 이유가 없다. null = 그대로다.
 *
 * <p>{@code version}만 필수다 — 낙관적 락은 무엇을 바꾸든 필요하다.
 */
public record IssueEditRequest(
        IssueStatus status,
        Long assigneeId,
        IssueType type,
        @Size(max = 300, message = "제목은 300자를 넘을 수 없습니다") String title,
        String content,
        Long version) {

    public IssueEditCommand toCommand() {
        return new IssueEditCommand(status, assigneeId, type, title, content);
    }

    /** 없으면 낙관적 락이 조용히 통과해 마지막 쓰기가 이긴다 (AC D3-2). */
    public long requiredVersion() {
        if (version == null) {
            throw new ValidationException("version은 필수입니다", "version");
        }

        return version;
    }
}
