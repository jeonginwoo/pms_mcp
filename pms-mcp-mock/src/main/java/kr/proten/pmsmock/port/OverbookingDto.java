package kr.proten.pmsmock.port;

import java.util.List;

/**
 * 과부하(보정 가동률 100% 초과) 인원 1명과 원인 배정 목록(FR-AI-12).
 */
public record OverbookingDto(
        long personId,
        String name,
        String team,
        String month,
        double totalMm,
        int rate,
        int adjustedRate,
        List<CauseDto> causes) {}
