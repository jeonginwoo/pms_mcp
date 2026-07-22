package kr.proten.pmsmock.port;

/**
 * 인원 1명의 월 가동률. rate는 무보정(%), adjustedRate는 직급계수 보정(%)이다.
 */
public record UtilizationEntryDto(
        long personId,
        String name,
        String team,
        double mm,
        int rate,
        int adjustedRate) {}
