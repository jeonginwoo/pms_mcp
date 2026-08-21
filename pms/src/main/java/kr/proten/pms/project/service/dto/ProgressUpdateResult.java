package kr.proten.pms.project.service.dto;

import kr.proten.pms.project.service.entity.Project;

/**
 * 진척률 갱신 결과 (AC A2-1·A2-2·A2-3).
 *
 * 요약 단계와 커밋 단계가 같은 형태를 쓴다 — 확인 카드가 보여 줄 값(현재 → 요청)이
 * 두 단계에서 동일해야 사용자가 같은 화면을 두 번 읽을 수 있다.
 *
 * @param committed   저장까지 끝났는가 (false = 확인 대기 요약)
 * @param completable 완료 처리가 가능한 상태가 되는가 (AC A2-3 안내값)
 */
public record ProgressUpdateResult(
        Long projectId,
        String name,
        int currentProgress,
        int requestedProgress,
        boolean committed,
        boolean completable,
        long version) {

    /** 확인 대기 요약 — DB는 아직 그대로다. */
    public static ProgressUpdateResult summary(Project project, int requestedProgress) {
        return new ProgressUpdateResult(
                project.getId(),
                project.getName(),
                project.getProgress(),
                requestedProgress,
                false,
                project.completableAt(requestedProgress),
                project.getVersion());
    }

    /** 커밋 완료 — 진척률·version은 저장된 값이다. */
    public static ProgressUpdateResult committedOf(Project project) {
        return new ProgressUpdateResult(
                project.getId(),
                project.getName(),
                project.getProgress(),
                project.getProgress(),
                true,
                project.isCompletable(),
                project.getVersion());
    }
}
