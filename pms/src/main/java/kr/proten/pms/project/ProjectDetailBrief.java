package kr.proten.pms.project;

import java.time.LocalDate;
import java.util.List;

/**
 * 프로젝트 상세 — MCP {@code ProjectDetail}이 채워지는 모양이다.
 *
 * <p>{@code version}을 싣는 이유: 진척률 수정(2단계 확인)에 필요한 version은 이 상세
 * 조회로 확보한다는 것이 도구 description의 약속이다.
 *
 * <p>{@code pm}·{@code participants}는 이름이다 — 어댑터가 id로 사람을 되묻지 않게 한다.
 * {@code team}·{@code division}은 {@link ProjectBrief}와 같은 규칙(PM의 소속)이다.
 */
public record ProjectDetailBrief(
        long id,
        String name,
        String client,
        String status,
        int progress,
        LocalDate startDate,
        LocalDate endDate,
        double contractMm,
        String engagement,
        String solution,
        String pm,
        List<String> participants,
        String team,
        String division,
        long version) {
}
