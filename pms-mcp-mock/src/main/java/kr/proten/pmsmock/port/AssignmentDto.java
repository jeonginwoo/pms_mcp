package kr.proten.pmsmock.port;

/**
 * 프로젝트 상세에 딸린 배정 1건. 근거 월(month)을 포함해 모델이 출처를 인용할 수 있게 한다.
 */
public record AssignmentDto(String personName, String month, double mm) {}
