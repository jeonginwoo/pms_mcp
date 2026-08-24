package kr.proten.pms.project;

/**
 * 유지보수 이관에 대해 project가 필요로 하는 것 — 구현은 maintenance 모듈이 가진다
 * (AC D1-1).
 *
 * <p><b>방향을 뒤집어 둔 이유</b>는 {@code AccountPort}와 다르다. 그쪽은 실재하는
 * 순환을 깨려고 생겼지만, project와 maintenance는 2026-08-25 실측으로 <b>서로를 전혀
 * 모르는 형제 모듈</b>이다(둘 다 person·audit·common만 쓴다). 즉 여기서는 어느 방향으로든
 * 간선을 그을 수 있었고, 그래서 <b>잠재 의존이 이미 가리키는 방향</b>을 골랐다:
 * maintenance는 이미 {@code sourceProjectId}를 저장하고 D4-2가 "원 프로젝트 링크"를
 * 요구하므로, maintenance가 project를 알게 되는 날이 온다. 그때 project → maintenance
 * 간선이 있었다면 순환이 되고 `ModularityTest`가 막는다.
 *
 * <p>그래서 <b>필요한 쪽(project)이 계약을 정의하고 만드는 쪽(maintenance)이 구현한다</b>
 * — project는 maintenance를 import하지 않고, 의존은 maintenance → project 한 방향이다
 * (2026-08-25 사용자 결정 — 공용 결정 기록).
 *
 * <p><b>권한은 이 포트를 넘어오지 않는다.</b> D1은 `[PM]`이고 그 판정은 호출 전에
 * project가 끝낸다({@code ProjectAction.HANDOVER}). 구현이 "계약 관리" 플래그를 다시
 * 보면 <b>그 플래그가 없는 PM은 자기 프로젝트를 이관할 수 없게 된다</b> — 그래서
 * 어댑터는 {@code ContractCommandService}(D2 쓰기 경로)를 재사용하지 않는다.
 * 관문의 유무가 계약을 가르는 것은 US-D3에서 이미 겪은 자리다.
 *
 * <p>계약의 표현(엔티티·id 발급·감사 기록·이관 이벤트 발행)은 전부 maintenance 안에
 * 남는다. project는 "계약이 생겼다"와 그 id만 안다.
 */
public interface HandoverPort {

    /**
     * 이관 계약과 사이트를 만든다 (AC D1-1) — {@code sourceProjectId}가 채워진다.
     *
     * <p>호출자의 트랜잭션에 참여하므로 <b>상태 전이가 롤백되면 계약도 남지 않는다</b>
     * (D1-2 원자성). 입력이 §4 표의 필수값을 채우지 못하면 400을 던지고, 그 던짐이
     * 상태 전이보다 <b>앞서</b> 일어나야 한다(D1-3 — "상태 전이도 미발생").
     *
     * <p><b>화자를 받지만 판정에 쓰지 않는다</b> — 감사 행위자와 이관 이벤트의
     * {@code handedOverBy}가 그것이다. 추측하면 append-only 로그에 하지 않은 사람이
     * 남으므로 넘겨야 하고, 그렇다고 구현이 그 값으로 권한을 다시 보는 것은 아니다
     * (US-D3의 {@code IssueCommandService}가 같은 이유로 화자를 받는다).
     *
     * <p><b>계약 id를 돌려주지 않는다</b>: project가 그것으로 할 일이 없다.
     * 응답은 {@code ProjectDetail}이고 감사의 계약 행은 maintenance가 남기며
     * 이관 이벤트도 maintenance가 발행한다(§8 발행자 = maintenance). 쓰지 않는 값을
     * 돌려주면 다음 사람이 그것을 쓸 자리를 찾게 된다.
     */
    void createHandoverContract(long callerPersonId, long projectId, HandoverSpec spec);
}
