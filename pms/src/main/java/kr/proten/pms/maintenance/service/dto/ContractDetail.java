package kr.proten.pms.maintenance.service.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import kr.proten.pms.maintenance.service.entity.ContractStatus;
import kr.proten.pms.person.PersonRef;

/**
 * 계약 상세 (AC D4-2) — 계약 + 사이트 목록(담당 엔지니어) + 연락처 + 이슈 요약 +
 * 원 프로젝트 링크.
 *
 * <p>연락처는 별도 목록이 아니라 {@link SiteView} 안에 들어 있다 — 연락처는 사이트에
 * 붙는 것이고, 평평하게 펴면 화면이 다시 사이트별로 묶어야 한다.
 *
 * @param sourceProjectId 이관으로 생긴 계약이면 원 프로젝트, 직접 등록이면 null
 * @param status     한국어 라벨("유지") — 표시용
 * @param statusCode 열거 이름(ACTIVE) — 수정 폼(D2-2)이 선택 상태를 되채우는 값이다
 *                   (2026-08-24 신설). 라벨만 주면 화면이 라벨→이름 표를 또 갖게 되고
 *                   그 표가 서버 열거와 어긋나는 날이 온다
 * @param issueCountByStatus 상태별 이슈 건수 — 상세에서 이슈 행 전체를 싣지 않는다
 */
public record ContractDetail(
        long id,
        Long sourceProjectId,
        String contractor,
        String name,
        String status,
        ContractStatus statusCode,
        String sheetSection,
        LocalDate contractDate,
        String contractDateNote,
        LocalDate startDate,
        LocalDate endDate,
        Long amount,
        Long monthlyAmount,
        PersonRef salesRep,
        String category,
        String targetInfra,
        String regularCheck,
        String note,
        List<SiteView> sites,
        Map<String, Long> issueCountByStatus,
        long version) {
}
