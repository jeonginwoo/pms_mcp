package kr.proten.pms.project.service.dto;

/**
 * 진척률 갱신 입력 (AC A2-1·A2-2) — 2단계 확인 프로토콜의 요청 형태.
 *
 * @param version   낙관적 락 버전 — 확인 단계에서만 검사한다 (AC A2-6)
 * @param confirmed false면 변경 요약만 돌려주고 저장하지 않는다
 */
public record UpdateProgressCommand(long projectId, int progress, long version,
        boolean confirmed) {
}
