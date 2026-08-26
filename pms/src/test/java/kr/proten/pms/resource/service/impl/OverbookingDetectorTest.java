package kr.proten.pms.resource.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.List;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.project.AssignmentChanged;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 배정 변경 → 과부하 판정 리스너의 진입 조건 (ROADMAP B1-3 · AC F1-1의 전제).
 *
 * <p>여기서 잠그는 것은 <b>비활성 인원을 건너뛰는가</b> 하나다. 화자가 대상 본인이라
 * {@code RequesterResolver}가 활성 인원만 찾고, 비활성이면 404로 리스너가 통째로
 * 실패한다 — 퇴사 처리(§12 ③)가 그 조합을 일상적으로 만든다(배정 종료와 비활성이
 * 한 트랜잭션이고 리스너는 커밋 후에 온다).
 */
@ExtendWith(MockitoExtension.class)
class OverbookingDetectorTest {
    private static final long PERSON_ID = 1102L;

    @Mock
    private UtilizationCalculator calculator;
    @Mock
    private PersonDirectoryService personDirectoryService;
    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private OverbookingDetector detector;

    @Test
    @DisplayName("비활성 인원은 계산조차 하지 않는다 — 퇴사자에게 보낼 과부하 알림이 없다")
    void inactivePersonIsSkipped() {
        // Given: 퇴사 처리가 배정을 끊고 사람을 비활성한 뒤 커밋됐다
        when(personDirectoryService.existsActive(PERSON_ID)).thenReturn(false);

        // When
        detector.onAssignmentChanged(changed());

        // Then: 계산에 들어가면 화자 해석이 404로 터진다
        verifyNoInteractions(calculator);
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("활성 인원은 영향 월마다 지금 값을 읽는다")
    void activePersonIsChecked() {
        // Given
        when(personDirectoryService.existsActive(PERSON_ID)).thenReturn(true);
        when(calculator.calculate(anyLong(), any())).thenReturn(List.of());

        // When
        detector.onAssignmentChanged(changed());

        // Then: 과부하가 아니면 발행은 없다 — 읽었다는 것만 본다
        verify(calculator).calculate(anyLong(), any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    private static AssignmentChanged changed() {
        return new AssignmentChanged(
                AssignmentChanged.Kind.CLOSED,
                11L,
                null,
                PERSON_ID,
                List.of(YearMonth.of(2026, 8)));
    }
}
