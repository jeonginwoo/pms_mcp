package kr.proten.pms.maintenance.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.proten.pms.maintenance.service.dto.IssueCommand;
import kr.proten.pms.maintenance.service.entity.IssueType;

/**
 * 이슈 등록 요청 (AC D3-1) — {@code {siteId, type, 제목}}이 AC가 적은 전부다.
 *
 * <p>담당자·상태·접수일 칸이 없는 것은 누락이 아니다 — 서버가 정한다
 * ({@code IssueCommand} 주석: 담당은 사이트의 엔지니어, 상태는 접수, 접수일은 오늘).
 */
public record IssueRequest(
        @NotNull(message = "사이트는 필수입니다") Long siteId,
        @NotNull(message = "이슈 유형은 필수입니다") IssueType type,
        @NotBlank(message = "제목은 필수입니다")
        @Size(max = 300, message = "제목은 300자를 넘을 수 없습니다") String title,
        /**
         * 본문 — <b>선택이다</b> (2026-08-26 신설). 안 보내면 제목만 있는 이슈가 되고,
         * 그것이 시드 267건의 모습이다. 길이 상한을 두지 않는 것은 컬럼이 {@code text}이고
         * 처리 내용이 얼마나 길어야 하는지 AC가 정하지 않았기 때문이다.
         */
        String content) {

    public IssueCommand toCommand() {
        return new IssueCommand(siteId, type, title, content);
    }
}
