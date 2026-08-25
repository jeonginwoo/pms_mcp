package kr.proten.pms.project.service;

import kr.proten.pms.project.service.dto.ProjectPermissionMatrix;
import kr.proten.pms.project.service.dto.UpdateProjectPermissionsCommand;

/**
 * 프로젝트별 권한 매트릭스 조회·조정 (US-A8).
 *
 * <p>계약을 따로 두는 이유는 <b>판정 축이 다르기</b> 때문이다(pms/CLAUDE.md — "한
 * 도메인 관심사당 하나"): 조회는 가시성이 판정하고(A8-1 — 화면이 잠금 표시를 그려야
 * 하므로 PL·참여자도 읽는다) 저장은 <b>PM만</b>이다(A8-3). 같은 라우트 묶음에서
 * 읽기와 쓰기가 다른 문을 쓰는 모양은 maintenance의 조회/계약쓰기/이슈쓰기 3분할과
 * 같은 자리다.
 */
public interface ProjectPermissionService {

    /**
     * 역할×기능 매트릭스 (AC A8-1) — 기본값 + override 병합 결과 + 셀별 고정 여부.
     * 가시성 밖은 404 은닉이다(A3-2와 같은 규칙).
     */
    ProjectPermissionMatrix getMatrix(long callerPersonId, long projectId);

    /**
     * 매트릭스 저장 (AC A8-2) — 전체 교체이며 기본값과 같은 칸은 저장하지 않는다.
     * PM만(A8-3) · 고정 칸 포함은 422이고 아무것도 바뀌지 않는다(A8-4) ·
     * {@code version} 불일치는 409다(A8-7).
     */
    ProjectPermissionMatrix updateOverrides(
            long callerPersonId, long projectId, UpdateProjectPermissionsCommand command);
}
