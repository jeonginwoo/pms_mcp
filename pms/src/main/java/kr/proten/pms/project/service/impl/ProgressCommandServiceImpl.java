package kr.proten.pms.project.service.impl;

import kr.proten.pms.project.ProgressCommandService;
import kr.proten.pms.project.ProgressResult;
import kr.proten.pms.project.service.ProjectLifecycleService;
import kr.proten.pms.project.service.dto.ProgressUpdateResult;
import kr.proten.pms.project.service.dto.UpdateProgressCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ProgressCommandService} 구현 — 내부 유스케이스를 어댑터가 쓰는 모양으로 좁힌다.
 *
 * <p>2단계 확인 프로토콜을 여기서 다시 구현하지 않는다: {@code ProjectLifecycleService}가
 * 이미 갖고 있고, 두 곳에 두면 한쪽이 확인을 건너뛰는 길이 생긴다(구조 원칙 5).
 * 이 클래스가 하는 일은 커맨드 조립과 결과 이름 맞추기뿐이다.
 */
@Service
@Transactional
class ProgressCommandServiceImpl implements ProgressCommandService {
    private final ProjectLifecycleService projectLifecycleService;

    ProgressCommandServiceImpl(ProjectLifecycleService projectLifecycleService) {
        this.projectLifecycleService = projectLifecycleService;
    }

    @Override
    public ProgressResult updateProgress(
            long callerPersonId, long projectId, int percent, long version, boolean confirmed) {
        ProgressUpdateResult result = projectLifecycleService.updateProgress(
                callerPersonId,
                new UpdateProgressCommand(projectId, percent, version, confirmed));

        return new ProgressResult(
                result.committed(),
                result.projectId(),
                result.name(),
                result.currentProgress(),
                result.requestedProgress(),
                result.version(),
                result.completable());
    }
}
