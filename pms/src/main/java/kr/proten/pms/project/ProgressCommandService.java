package kr.proten.pms.project;

/**
 * 진척률 변경 — 모듈 밖(현재는 `/mcp` 어댑터)에 여는 유일한 <b>쓰기</b> 계약
 * (2026-08-23 신설).
 *
 * <p>구조 원칙 5의 자리다: 쓰기 도구는 {@code update_progress} 하나뿐이고 2단계 확인
 * (confirmed=false → 요약 → confirmed=true)이 필수다. 그 프로토콜은 내부
 * {@code ProjectLifecycleService.updateProgress}가 이미 구현하고 있으므로 이 계약은
 * 어댑터가 쓰는 모양으로 좁히기만 한다 — 프로토콜을 두 곳에 두면 한쪽이 확인을
 * 건너뛰는 길이 생긴다.
 *
 * <p>권한·가시성 판정은 내부 유스케이스가 한다(A2-4·A2-7·A8-5). 챗과 화면이 <b>같은
 * 서비스를 거치므로</b> 같은 거절을 받는다 — "챗에서 되는 것 = 화면에서 되는 것".
 */
public interface ProgressCommandService {
    /**
     * @param confirmed false면 커밋하지 않고 변경 요약만 돌려준다 (AC A2-1)
     * @return 실행 여부·직전 진척률·최신 version·완료 가능 여부
     */
    ProgressResult updateProgress(
            long callerPersonId, long projectId, int percent, long version, boolean confirmed);
}
