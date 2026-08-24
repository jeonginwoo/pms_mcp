package kr.proten.pms.project;

import java.time.LocalDate;
import java.util.List;

/**
 * 이관 시점에 받는 계약 필수 정보 (AC D1-1) — {@link HandoverPort}의 입력.
 *
 * <p><b>project 루트에 있는 것이 이상하지 않다</b>: 역포트의 입력은 정의하는 쪽의
 * 어휘여야 하고({@code AccountPort}가 {@code personId}·{@code email}을 그렇게 받는다),
 * 그러지 않으면 project가 maintenance의 DTO를 import하게 되어 간선이 되돌아온다.
 * 그래서 여기 담는 것은 <b>이관이 요구하는 최소 집합</b>이고, 계약의 나머지 칸
 * (시트 구간·정기점검·비고 같은 시드 유래 필드)은 maintenance가 비운 채 만든다 —
 * 이관 계약에는 그 사정이 없다.
 *
 * <p><b>사이트가 1개 이상 필수인 이유</b>(D1-1): 필수값을 이관 시점에 받으므로
 * "유지보수중인데 계약 정보 없는 프로젝트"가 원천적으로 못 생긴다. 각 사이트의
 * {@code engineerId}도 그 요구의 일부다 — 담당 없는 사이트로 이관하면 그 사이트에
 * 올라온 이슈가 영원히 미배정으로 남는다(D3-1이 사이트에서 담당을 가져온다).
 *
 * @param contractor 계약사 — §4 표의 {@code not null} 칸이다
 * @param name       계약명
 * @param amount     계약 금액 · {@code monthlyAmount} 월 금액
 */
public record HandoverSpec(
        String contractor,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        Long amount,
        Long monthlyAmount,
        List<Site> sites) {

    /**
     * 이관과 함께 만드는 사이트 (AC D1-1).
     *
     * <p>연락처를 받지 않는다: D1-1이 요구한 것은 사이트명과 담당 엔지니어이고,
     * 연락처는 이관 뒤 D2-4로 붙인다. 이관 폼에 없는 칸을 넣으면 PM이 계약 담당자의
     * 일을 대신하게 된다.
     */
    public record Site(String name, Long engineerId) {
    }
}
