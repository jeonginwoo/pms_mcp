package kr.proten.pms.resource;

import kr.proten.pms.common.exception.ValidationException;

/**
 * 가동률 조회 범위 — MCP `get_utilization`의 `scope` 파라미터 (PRD-host FR-AI-11).
 *
 * <p><b>이 낱말들을 resource가 갖는 이유</b>: "MY_TEAM이 누구인가"는 조직 트리와 화자
 * 가시성으로 답하는 도메인 질문이다. 어댑터가 조직 id로 바꿔서 넘기면 그 판정이 어댑터에
 * 남고, 웹(`?orgUnitId=`)과 챗이 같은 답을 낸다는 보장이 약해진다 — 구조 원칙 3이 판정을
 * 애플리케이션 계층에 두라는 것도 같은 이유다(2026-08-24 결정 기록 ②).
 *
 * <p>웹에 이 열거가 나가지 않는 것도 의도다: 화면은 조직 트리에서 노드를 골라
 * `?orgUnitId=`를 보내므로 "내 팀"이라는 상대 개념이 필요 없다. 이것은 <b>화자밖에 모르는
 * 호출자</b>(챗)를 위한 어휘다.
 */
public enum UtilizationScope {
    /** 본인 — `personId` 없이 화자 자신 (개인 지정이므로 billable과 무관하다 · AC C1-5) */
    ME,
    /** 화자의 팀 subtree 집계 */
    MY_TEAM,
    /** 화자의 부문 subtree 집계 */
    DIVISION,
    /** 전사 집계 — 실제 상한은 화자의 가시성이다(도구 문구: "조회 가능한 범위는 서버가 판정한다") */
    COMPANY,
    /** `personId`로 지정한 개인 */
    PERSON;

    /**
     * 도구 파라미터 문자열 해석 — 모르는 값은 예외다.
     *
     * <p>조용히 기본값으로 떨어뜨리지 않는 이유는 {@code ProjectLookupService}의 상태
     * 라벨과 같다: 모델은 카탈로그에 없는 낱말을 지어낸다(B2-1 실측 — `scope=COMPANY`가
     * 그렇게 발견됐다). 임의로 넓은 범위로 해석하면 사용자는 <b>틀린 범위의 답을 맞는
     * 답으로 받는다</b>.
     */
    public static UtilizationScope from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid();
        }

        for (UtilizationScope candidate : values()) {
            if (candidate.name().equalsIgnoreCase(raw.trim())) {
                return candidate;
            }
        }

        throw invalid();
    }

    private static ValidationException invalid() {
        return new ValidationException(
                "조회 범위는 ME/MY_TEAM/DIVISION/COMPANY/PERSON 중 하나여야 합니다", "scope");
    }
}
