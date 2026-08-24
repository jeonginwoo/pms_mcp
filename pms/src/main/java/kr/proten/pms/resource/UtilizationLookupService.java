package kr.proten.pms.resource;

import java.time.YearMonth;
import java.util.List;

/**
 * 가동률 조회 — 모듈 밖(현재는 `/mcp` 어댑터)에 여는 계약 (2026-08-24 신설,
 * 공용 결정 기록. 소유자 = PMS 담당, 배선 = MCP 담당).
 *
 * <p>내부 `UtilizationQueryService`를 그대로 올리지 않는 이유는 두 가지다.
 * ①<b>범위를 지정하는 방식이 다르다</b>: 웹은 조직 트리에서 고른 `?orgUnitId=`를 주지만
 * 챗은 화자밖에 모르므로 {@link UtilizationScope}를 준다 — "내 팀"을 인원 집합으로 푸는
 * 것이 이 계약이 하는 일이다. ②<b>과부하 응답이 원인을 요구한다</b>: 웹 화면은 원인을
 * 쓰지 않아 내부 dto가 프로젝트별 기여분을 버린다.
 *
 * <p><b>가시성은 판정한다</b>: 두 메서드 모두 화자 id를 받고, 집계 범위의 상한은
 * 언제나 화자의 조직 가시성이다(상위 PRD §4-4). 개인 지정이 가시성 밖이면 부재와 같은
 * 404다 — 챗에서 보이는 것 = 화면에서 보이는 것.
 *
 * <p>집계는 `billable` 인원만 센다(AC C1-5). 개인 지정({@code ME}·{@code PERSON})은 그
 * 규칙과 무관하다 — 지원 조직 인원도 자기 가동률은 갖는다.
 */
public interface UtilizationLookupService {

    /**
     * 범위별 가동률 (MCP `get_utilization` · AC C1-1·C1-5·C1-6).
     *
     * @param targetPersonId {@link UtilizationScope#PERSON}일 때 필수, 그 밖에는 무시된다
     * @throws kr.proten.pms.common.exception.ValidationException
     *         `scope=PERSON`인데 `targetPersonId`가 없을 때
     * @throws kr.proten.pms.common.exception.NotFoundException
     *         개인 지정 대상이 없거나 가시성 밖일 때 (은닉)
     */
    List<UtilizationBrief> find(
            long callerPersonId, YearMonth month, UtilizationScope scope, Long targetPersonId);

    /**
     * 과부하 인원과 원인 (MCP `list_overbooked` · AC C1-3).
     *
     * <p>범위 파라미터가 없다: 도구가 월만 받고 "범위는 조회자의 가시성으로 서버가
     * 판정한다"고 약속했다(FR-AI-12). 즉 {@link UtilizationScope#COMPANY}와 같은 상한이다.
     */
    List<OverbookedBrief> findOverbooked(long callerPersonId, YearMonth month);
}
