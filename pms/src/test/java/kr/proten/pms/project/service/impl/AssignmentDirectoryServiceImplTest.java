package kr.proten.pms.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import kr.proten.pms.project.MonthlyAssignment;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 배정 조회 계약 단위 테스트 (AC C1-1 — 가동률 분자의 원천).
 *
 * 이 계약이 지켜야 하는 것은 둘이다: 월을 날짜 경계로 정확히 펴는 것과,
 * 판정을 하지 않는 것(모집단은 호출자가 정한다 — 두 곳에서 거르면 정본이 갈린다).
 */
@ExtendWith(MockitoExtension.class)
class AssignmentDirectoryServiceImplTest {
    @Mock
    private ProjectAssignmentRepository assignmentRepository;
    @InjectMocks
    private AssignmentDirectoryServiceImpl service;

    @Test
    @DisplayName("C1-1 — 월을 그 달 1일~말일로 펴서 질의한다")
    void findInMonth_expandsMonthToDateBounds() {
        when(assignmentRepository.findOverlapping(anyCollection(), any(), any()))
                .thenReturn(List.of());

        service.findInMonth(YearMonth.of(2026, 2), Set.of(18L));

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(assignmentRepository).findOverlapping(anyCollection(), from.capture(), to.capture());

        assertThat(from.getValue()).isEqualTo(LocalDate.of(2026, 2, 1));
        // 윤년 2월 — 말일을 상수로 두면 이 경계가 조용히 틀어진다
        assertThat(to.getValue()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("빈 명단이면 질의하지 않는다 — in () 은 DB마다 다르게 군다")
    void findInMonth_emptyRoster_skipsQuery() {
        List<MonthlyAssignment> found = service.findInMonth(YearMonth.of(2026, 8), Set.of());

        assertThat(found).isEmpty();
        verify(assignmentRepository, never()).findOverlapping(anyCollection(), any(), any());
    }

    @Test
    @DisplayName("합산하지 않고 행 그대로 넘긴다 — 과부하 원인이 프로젝트별로 필요하다")
    void findInMonth_returnsRowsNotSums() {
        MonthlyAssignment first = new MonthlyAssignment(18L, 1L, "명화공업 MES", 0.5);
        MonthlyAssignment second = new MonthlyAssignment(18L, 2L, "SK온 EUE공장", 0.7);
        when(assignmentRepository.findOverlapping(anyCollection(), any(), any()))
                .thenReturn(List.of(first, second));

        List<MonthlyAssignment> found = service.findInMonth(YearMonth.of(2026, 8), Set.of(18L));

        assertThat(found).containsExactly(first, second);
    }
}
