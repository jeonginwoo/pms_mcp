package kr.proten.pms.resource.service.impl;

import java.time.YearMonth;
import java.util.List;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.project.AssignmentChanged;
import kr.proten.pms.resource.OverbookingDetected;
import kr.proten.pms.resource.service.dto.UtilizationQuery;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 배정이 바뀌면 그 사람의 영향 월을 다시 계산해 과부하를 알린다
 * (ROADMAP B1-3 · §8 {@code OverbookingDetected} · AC F1-1의 전제).
 *
 * <p><b>재계산이라는 말이 오해를 부른다</b>: 저장된 값을 고치는 것이 아니다. 가동률은
 * 조회 시점 계산이고 캐시가 없으므로(2026-08-06) 여기서 하는 일은 <b>지금 값을 읽어
 * 과부하인지 보는 것</b>뿐이다. B1-3이 "커밋 후 가동률 재계산"이라고 적힌 것은 캐시를
 * 전제하던 시절의 문구이고, 실제로 필요한 것은 <b>넘어간 순간을 붙잡는 것</b>이다.
 *
 * <p><b>왜 resource가 발행하는가</b>(§8): 과부하 판정은 산식·모집단과 한 몸이라
 * project가 하면 그 규칙이 두 모듈에 생긴다. project는 "배정이 바뀌었다"는 사실만 낸다.
 *
 * <p><b>개인 지정 조회로 묻는다</b>(집계가 아니다): 집계는 {@code billable=false}를 모집단에서
 * 빼므로(C1-5) 지원 조직 인원의 과부하를 못 본다. 알림은 "이 사람이 지금 물려 있다"는
 * 사실이지 단가 집계가 아니다.
 *
 * <p>화자는 <b>대상 본인</b>이다 — 개인 지정 조회의 가시성 관문이 "자기 자신은 언제나
 * 보인다"이므로, 배정을 고친 사람의 가시성에 결과가 좌우되지 않는다.
 *
 * <p><b>그래서 비활성 인원은 건너뛴다</b>(2026-08-26 신설): 화자가 본인이므로
 * {@code RequesterResolver}가 <b>활성 인원만</b> 찾고, 비활성이면 404를 던져 이 리스너가
 * 통째로 실패한다. 퇴사 처리(E2-3 · §12 ③)가 그 조합을 <b>일상적으로</b> 만든다 —
 * 참여자 배정을 종료하면서 같은 트랜잭션에서 사람을 비활성하는데, 커밋 후에 도착하는
 * 이 리스너에게는 이미 비활성이다. 퇴사자에게 과부하 알림을 보낼 이유도 없다.
 */
@Component
class OverbookingDetector {
    private static final double THRESHOLD = 100.0;

    private final UtilizationCalculator calculator;
    private final PersonDirectoryService personDirectoryService;
    private final ApplicationEventPublisher events;

    OverbookingDetector(
            UtilizationCalculator calculator,
            PersonDirectoryService personDirectoryService,
            ApplicationEventPublisher events) {
        this.calculator = calculator;
        this.personDirectoryService = personDirectoryService;
        this.events = events;
    }

    @ApplicationModuleListener
    void onAssignmentChanged(AssignmentChanged event) {
        if (!personDirectoryService.existsActive(event.personId())) {
            return;
        }

        event.affectedMonths().forEach(month -> checkMonth(event.personId(), month));
    }

    private void checkMonth(long personId, YearMonth month) {
        List<PersonUtilization> rows = calculator.calculate(
                personId, new UtilizationQuery(month, personId, null, false));

        rows.stream()
                .filter(row -> row.basicPct() > THRESHOLD)
                .forEach(row -> events.publishEvent(
                        new OverbookingDetected(personId, month, row.basicPct())));
    }
}
