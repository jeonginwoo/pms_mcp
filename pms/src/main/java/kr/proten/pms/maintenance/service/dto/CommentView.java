package kr.proten.pms.maintenance.service.dto;

import java.time.Instant;
import kr.proten.pms.person.PersonRef;

/**
 * 이슈 코멘트 한 줄.
 *
 * @param updatedAt 마지막 수정 시각 — null이면 한 번도 고치지 않았다 (AC D3-7,
 *                  2026-08-26). 화면이 "수정됨"을 그릴 근거이고, 수정 흔적을 지우지
 *                  않겠다는 판단이 이 칸이다(엔티티 javadoc 참조).
 */
public record CommentView(
        long id, PersonRef author, String content, Instant createdAt, Instant updatedAt) {
}
