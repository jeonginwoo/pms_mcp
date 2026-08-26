package kr.proten.pms.project.service.impl;

import kr.proten.pms.person.AssignmentCountPort;
import kr.proten.pms.project.ProjectStatus;
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
 * <p>세는 것은 <b>지금 물려 있는 배정</b>이다 — 배정이 종료되지 않았고 <b>프로젝트도
 * 아직 진행 중</b>인 것({@link ProjectStatus#isLive()}).
 *
 * <p><b>이 주석의 옛 판이 틀렸었다</b>(2026-08-26 실측·정정). 그때는 "프로젝트 상태로
 * 거르지 않는 이유는 완료 시 배정이 이미 종료 처리되기 때문(AC B2-1)"이라고 적었는데,
 * B2-1은 {@code DELETE /assignments/{id}} 즉 <b>명시적 수동 종료</b>이고 완료 시 자동
 * 종료를 정한 AC는 없다. 그 결과 시드된 DB에서 ACTIVE 배정 462건 중 <b>384건(83%)이
 * 완료 프로젝트</b>의 것이었고, 이동 경고는 한 사람에게 "진행 중인 배정 128건"이라
 * 답했다 — 실제로 물려 있는 것은 5건이었다.
 *
 * <p>고친 방향은 <b>판정</b>이다(2026-08-26 사용자 결정): 데이터를 자동 종료로 수렴시키면
 * 종료 경로가 B2-1과 둘로 갈리고 재개(A7-3)에 되살리기 규칙이 붙는다. 이동 경고가 묻는
 * 것은 "이 사람이 지금 어디에 물려 있는가"이므로 질의가 그 질문 그대로 답하면 된다.
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
        return assignmentRepository.countLiveByPerson(
                personId, AssignmentStatus.ACTIVE, ProjectStatus.live());
    }
}
