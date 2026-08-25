/*
 * 화면 노출 판정 — 누를 수 없는 버튼을 보여 주지 않기 위한 것이다.
 *
 * 최종 판정은 언제나 서버다(같은 규칙이 서비스 계층에 있고, 여기서 통과시켜도
 * 서버가 403으로 막는다).
 *
 * **2026-08-26(US-A8)부터 이 파일은 §4-2 표를 갖지 않는다.** 프로젝트별 권한 커스텀이
 * 들어오면서 "역할 → 가능 여부"가 프로젝트마다 달라졌기 때문이다 — 기본값을 여기 적어
 * 두면 참여자 진척률을 끈 프로젝트에서 **그 참여자에게 버튼이 그대로 보이고** 누른
 * 뒤에야 403을 만난다. 병합은 서버가 하고(`GET /projects/{id}/permissions`) 화면은
 * 그 답(`ProjectPermissionMatrix`)을 읽기만 한다.
 *
 * 매트릭스가 아직 없으면(로딩 중) **막는 쪽으로 답한다**: 잠깐 못 누르는 것은 불편이고,
 * 잠깐 보였다 사라지는 버튼은 사용자가 권한을 오해하는 원인이다.
 */
import type {
  MeView, ProjectAction, ProjectDetail, ProjectPermissionMatrix, ProjectRole,
} from './types/api'

/**
 * 이 프로젝트에서 화자의 역할 — 배정이 정본이고, "전 프로젝트 관리" 플래그
 * 보유자는 모든 프로젝트에서 PM으로 간주된다(§4-1 치환).
 */
export function myRole(me: MeView | null, detail: ProjectDetail | null): ProjectRole | null {
  if (!me || !detail) {
    return null
  }

  if (me.manageAllProjects) {
    return 'PM'
  }

  return detail.assignments.find((assignment) => assignment.personId === me.id)?.role ?? null
}

/**
 * 그 기능을 지금 이 프로젝트에서 할 수 있는가 — **서버가 병합한 매트릭스**를 읽는다.
 * 미배정(역할 없음)은 언제나 false다: 매트릭스는 역할별 표이고 역할이 없으면 칸이 없다.
 */
export function can(
  me: MeView | null,
  detail: ProjectDetail | null,
  matrix: ProjectPermissionMatrix | null,
  action: ProjectAction,
): boolean {
  const role = myRole(me, detail)

  if (!role || !matrix) {
    return false
  }

  return matrix.cells.some(
    (cell) => cell.role === role && cell.action === action && cell.allowed)
}

/** 정보 수정·상태 전이 (A5-3) */
export function canEditInfo(
  me: MeView | null, detail: ProjectDetail | null, matrix: ProjectPermissionMatrix | null,
): boolean {
  return can(me, detail, matrix, 'EDIT_INFO')
}

/** 배정·M/M·PM 교체 (B1-4·A6-2) */
export function canAssign(
  me: MeView | null, detail: ProjectDetail | null, matrix: ProjectPermissionMatrix | null,
): boolean {
  return can(me, detail, matrix, 'ASSIGN')
}

/** 진척률 (A2-1) */
export function canUpdateProgress(
  me: MeView | null, detail: ProjectDetail | null, matrix: ProjectPermissionMatrix | null,
): boolean {
  return can(me, detail, matrix, 'PROGRESS')
}

/** 완료 처리·재개 (A7-1·A7-3) — 진척률과 별개 토글이다(§4-2는 둘을 한 칸으로 묶는다) */
export function canCompleteOrReopen(
  me: MeView | null, detail: ProjectDetail | null, matrix: ProjectPermissionMatrix | null,
): boolean {
  return can(me, detail, matrix, 'COMPLETE_REOPEN')
}

/** 유지보수 이관 (D1) — §4-2 고정 행이라 프로젝트별로 달라지지 않는다 */
export function canHandover(
  me: MeView | null, detail: ProjectDetail | null, matrix: ProjectPermissionMatrix | null,
): boolean {
  return can(me, detail, matrix, 'HANDOVER')
}

/**
 * 삭제 (A4-1 + 2026-08-22 결정) — **매트릭스 밖이다**.
 *
 * 판정 축이 둘이고(프로젝트 역할 · 조직 기능 플래그) §4-2의 고정 행이라 `ProjectAction`에
 * 없다. 서버도 같은 이유로 `requireDelete`를 따로 둔다 — 여기만 매트릭스를 쓰면
 * "생성 플래그 보유자는 배정되지 않아도 지운다"는 확장이 사라진다.
 */
export function canDelete(me: MeView | null, detail: ProjectDetail | null): boolean {
  return myRole(me, detail) === 'PM' || me?.createProject === true
}

/** 권한 매트릭스 조정 (A8-3) — PM만. 조회는 가시성 범위 전원이라 판정이 없다 */
export function canEditPermissions(
  me: MeView | null, detail: ProjectDetail | null,
): boolean {
  return myRole(me, detail) === 'PM'
}
