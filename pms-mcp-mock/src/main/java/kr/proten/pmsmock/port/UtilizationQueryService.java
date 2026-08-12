package kr.proten.pmsmock.port;

import java.util.List;

import kr.proten.pmsmock.port.dto.OverbookedEntry;
import kr.proten.pmsmock.port.dto.UtilizationEntry;

/**
 * 실전 계약: utilization 모듈 애플리케이션 서비스.
 * scope: ME / MY_TEAM / DIVISION / COMPANY / PERSON (COMPANY는 2026-08-11 결정 ③ — 전사 가시성 없는 호출자는 404 은닉).
 * 팀·부문·전사 집계 모집단 = billable=true (상위 PRD §3). 개인 지정 조회는 billable 무관.
 * 응답에는 team·division 동봉 — 집계를 소속별로 정리할 수 있게 (C1-6과 동일 계약).
 */
public interface UtilizationQueryService {

    /** month 형식 "yyyy-MM". scope=PERSON일 때만 personId 사용. */
    List<UtilizationEntry> getUtilization(int callerId, String month, String scope, Integer personId);

    /** 보정 가동률 100% 초과 인원 + 원인 배정. 범위는 호출자 가시성으로 서버가 판정. */
    List<OverbookedEntry> listOverbooked(int callerId, String month);
}
