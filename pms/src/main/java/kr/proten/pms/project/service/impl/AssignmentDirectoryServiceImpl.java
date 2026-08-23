package kr.proten.pms.project.service.impl;

import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import kr.proten.pms.project.AssignmentDirectoryService;
import kr.proten.pms.project.MonthlyAssignment;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AssignmentDirectoryService} 구현 — 월 경계를 날짜로 펴서 저장소에 넘긴다.
 *
 * <p>판정이 없는 계약이다: 가시성·billable·모집단은 호출자(resource)가 이미 정하고
 * 명단으로 넘기므로 여기서 다시 거르지 않는다. 두 곳에서 거르면 어느 쪽이 정본인지
 * 모르게 된다.
 */
@Service
@Transactional(readOnly = true)
class AssignmentDirectoryServiceImpl implements AssignmentDirectoryService {
    private final ProjectAssignmentRepository assignmentRepository;

    AssignmentDirectoryServiceImpl(ProjectAssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }

    @Override
    public List<MonthlyAssignment> findInMonth(YearMonth month, Collection<Long> personIds) {
        if (personIds.isEmpty()) {
            return List.of();
        }

        return assignmentRepository.findOverlapping(
                personIds, month.atDay(1), month.atEndOfMonth());
    }
}
