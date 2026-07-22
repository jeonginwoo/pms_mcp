package kr.proten.pmsmock.port;

import java.util.List;

/**
 * 가동률 조회 응답. 모델이 "N월 기준"을 인용할 수 있도록 근거 월을 포함한다(S-1 수용 기준).
 */
public record UtilizationReportDto(String month, String scope, List<UtilizationEntryDto> entries) {}
