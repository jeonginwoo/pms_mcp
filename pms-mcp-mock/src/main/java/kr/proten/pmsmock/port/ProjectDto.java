package kr.proten.pmsmock.port;

import java.util.List;

/**
 * 프로젝트 응답. LLM에 직행하므로 필요한 필드만 담는다.
 * assignments는 projectId 상세 조회일 때만 채워지고 목록 검색에서는 null이다.
 */
public record ProjectDto(
        long id,
        String name,
        String client,
        String status,
        int progress,
        String startDate,
        String endDate,
        List<AssignmentDto> assignments) {}
