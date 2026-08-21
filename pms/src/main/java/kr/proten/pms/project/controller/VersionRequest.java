package kr.proten.pms.project.controller;

import jakarta.validation.constraints.NotNull;

/**
 * version만 담는 본문 (AC A7-1·A7-3 — `POST /complete`·`/reopen`).
 * 완료·재개는 입력값이 없는 행위다: 무엇으로 바뀌는지는 §5가 정하고, 누가 언제
 * 했는지는 감사 로그가 담는다(A7-3 "사유 입력 없음"). 남는 것은 낙관적 락뿐이다.
 */
public record VersionRequest(@NotNull(message = "version은 필수입니다") Long version) {
}
