package kr.proten.pms.mcp;

import java.util.List;


/** 실전 계약: identity 모듈 애플리케이션 서비스 (PMS `GET /api/me`와 동일 서비스 — FR-AI-16) */
public interface PersonQueryService {

    /** 본인 식별 — id·이름·팀·부문·권한 그룹명. 유효 권한 미반환. */
    WhoamiResult whoami(int callerId);

    /** 인력 검색 (FR-AI-13) — 호출자의 인력 가시성 내에서만. name·team은 null 허용. */
    List<PersonSummary> findPeople(int callerId, String name, String team);
}
