package kr.proten.pms.maintenance.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 코멘트 추가 요청 (AC D3-3) — append-only라 {@code version}이 없다.
 *
 * <p>칸이 하나뿐인데 record인 이유는 §7이 본문을 JSON 객체로 정했기 때문이다.
 * 문자열 하나를 그대로 받으면 나중에 칸이 늘 때 본문 모양이 바뀐다.
 */
public record CommentRequest(
        @NotBlank(message = "코멘트 내용은 필수입니다")
        @Size(max = 2000, message = "코멘트는 2000자를 넘을 수 없습니다") String content) {
}
