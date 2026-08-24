package kr.proten.pms.project.service.impl;

import kr.proten.pms.person.AssignmentCountPort;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.service.entity.AssignmentStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * person이 정의한 {@link AssignmentCountPort}를 project가 구현한다 (2026-08-24).
 *
 * <p>이 방향이 아니면 순환이다 — {@code AccountPort}(person 정의 · auth 구현)와 같은
 * 자리이고 이유도 같다. 구현이 {@code service/impl}에 있는 것도 같은 이유다:
 * 계약은 person의 것이고 project는 그것을 채울 뿐이라 밖으로 열 면이 없다.
 *
 * <p>세는 것은 <b>살아 있는 배정</b>이다(종료되지 않은 것). 프로젝트 상태로 거르지
 * 않는 이유는 완료 시 배정이 이미 종료 처리되기 때문이고(AC B2-1), 이동 경고가 묻는
 * 것도 "지금 물려 있는가"다.
 */
@Component
@Transactional(readOnly = true)
class AssignmentCountAdapter implements AssignmentCountPort {
    private final ProjectAssignmentRepository assignmentRepository;

    AssignmentCountAdapter(ProjectAssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }

    @Override
    public long countActiveAssignments(long personId) {
        return assignmentRepository.countByPersonIdAndStatus(personId, AssignmentStatus.ACTIVE);
    }
}
