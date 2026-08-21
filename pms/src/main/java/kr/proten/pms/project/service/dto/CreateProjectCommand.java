package kr.proten.pms.project.service.dto;

import java.time.LocalDate;
import java.util.List;
import kr.proten.pms.project.service.entity.Engagement;

/**
 * 프로젝트 생성 입력 (AC A1-1·A1-4).
 * 상태·진척률은 규칙으로 정해지므로(계약대기·0) 입력에 없다.
 *
 * @param assignments PM 정확히 1명 필수 · PL·참여자는 0명 이상 (AC A1-4·A1-6)
 */
public record CreateProjectCommand(
        String client,
        String name,
        String solution,
        Engagement engagement,
        double contractMm,
        LocalDate startDate,
        LocalDate endDate,
        List<AssignmentSpec> assignments) {
}
