package kr.proten.pms.maintenance;

import java.util.List;
import java.util.Optional;

/**
 * 유지보수 조회 — 모듈 밖(현재는 `/mcp` 어댑터)에 여는 계약 (2026-08-23 신설).
 *
 * <p>모듈 내부 계약({@code MaintenanceQueryService}·{@code IssueQueryService})을 그대로
 * 올리지 않고 좁은 면을 따로 두는 이유는 {@code PersonLookupService} 선례와 같다:
 * 내부 계약은 웹 화면이 쓰는 페이지 봉투·필터를 갖는데(§7 page 응답) 어댑터는
 * 페이징을 쓰지 않고 "최근 N건 절단"만 약속했다. 두 요구를 한 계약에 담으면 어느
 * 쪽 규칙인지 읽는 사람이 매번 확인해야 한다.
 *
 * <p><b>가시성 판정이 없다</b>: 유지보수 조회는 전사 공개이고 404 은닉도 없다
 * (AC D4-3 — 계약·이슈는 팀 경계 없는 회사 공용 자산). 그래서 호출자 id를 받지 않는다.
 *
 * <p>상태·유형을 <b>한국어 라벨 문자열</b>로 받는다: 도구 파라미터가 그 형태이고
 * (`"예정/신규/유지/종료"` · `"장애/문의/요청"`), enum을 모듈 루트로 내보내면
 * 어댑터가 도메인 어휘에 묶인다. 모르는 라벨은 빈 결과가 아니라 예외다 — 오타를
 * 조용히 "필터 없음"으로 바꾸면 사용자가 틀린 답을 받는다.
 */
public interface MaintenanceLookupService {

    /**
     * 계약 검색 (MCP {@code search_maintenance} · AC D4-1).
     * keyword는 계약명·계약사·<b>사이트명</b> 부분 일치 — 사이트명이 45사이트 계약에
     * 도달하는 유일한 경로다(2026-08-11 결정).
     *
     * @param statusLabel {@code 예정|신규|유지|종료} 또는 null
     * @param limit 종료일 내림차순으로 이만큼만 — 도구가 약속한 절단(최근 50건)
     */
    List<ContractBrief> searchContracts(String keyword, String statusLabel, int limit);

    /**
     * 이슈·코멘트 조회 (MCP {@code list_maintenance_logs}).
     *
     * <p>id가 <b>계약 id면 소속 이슈 전체, 이슈 id면 그 이슈만</b>이다 — 도구가 그렇게
     * 약속했고, 계약 → 사이트 → 이슈 두 단계를 어댑터가 밟게 하면 같은 질의가 화면과
     * 어댑터에 두 벌 생긴다. 무엇으로 찾았는지는 {@link ContractIssues#matched}가 답한다.
     *
     * @return 계약도 이슈도 아닌 id면 빈 값 — 어댑터가 404 문구를 정한다
     */
    Optional<ContractIssues> logsOf(long id, String typeLabel, int limit);
}
