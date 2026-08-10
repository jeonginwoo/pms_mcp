package kr.proten.pmsmock.port;

import kr.proten.pmsmock.port.dto.UpdateProgressResult;

/**
 * 실전 계약: project 모듈의 진척률 수정 서비스 (FR-AI-15 — 유일한 쓰기).
 * 판정(합집합 — 상위 PRD §4-1)·낙관적 락·상태 규칙은 전부 이 계층 책임:
 * - 권한: 그 프로젝트의 PM/PL/참여자 또는 "전 프로젝트 관리" 그룹 플래그(PM 간주) — 아니면 403
 * - 완료 상태: 409 PROJECT_COMPLETED · version 불일치: 409 STALE_VERSION + 최신값
 * - percent=100: 저장하되 상태 전이 없음 — completable 안내 (2026-08-06 완료 전이 재설계)
 */
public interface ProgressCommandService {

    UpdateProgressResult updateProgress(int callerId, int projectId, int percent, int version, boolean confirmed);
}
