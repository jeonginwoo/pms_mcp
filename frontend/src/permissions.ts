/*
 * 화면 노출 판정 — 상위 PRD §4-1·§4-2 표를 **표시용으로만** 옮긴 것이다.
 *
 * 최종 판정은 언제나 서버다(같은 규칙이 서비스 계층에 있고, 여기서 통과시켜도
 * 서버가 403으로 막는다). 이 파일의 목적은 하나: 누를 수 없는 버튼을 보여 주지 않기.
 * 규칙이 바뀌면 서버(ProjectActionPermission)와 여기를 함께 고친다.
 */
import type { MeView, ProjectDetail, ProjectRole } from './types/api'

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

/** 정보 수정·상태 전이 (A5-3) — PM·PL. */
export function canEditInfo(me: MeView | null, detail: ProjectDetail | null): boolean {
  const role = myRole(me, detail)

  return role === 'PM' || role === 'PL'
}

/** 배정·M/M·PM 교체 (B1-4·A6-2) — PM만. */
export function canAssign(me: MeView | null, detail: ProjectDetail | null): boolean {
  return myRole(me, detail) === 'PM'
}

/** 진척률·완료 처리·재개 (A2-1·A7-1) — 배정 전원. */
export function canUpdateProgress(me: MeView | null, detail: ProjectDetail | null): boolean {
  return myRole(me, detail) !== null
}

/** 삭제 (A4-1 + 2026-08-22 결정) — PM 또는 "프로젝트 생성" 플래그 보유자. */
export function canDelete(me: MeView | null, detail: ProjectDetail | null): boolean {
  return myRole(me, detail) === 'PM' || me?.createProject === true
}

/**
 * 유지보수 이관 (D1) — **PM만**이다.
 *
 * `canUpdateProgress`(배정 전원)나 `canAssign`을 빌려 쓰지 않는 이유는 서버와 같다:
 * 판정 칸은 행위 하나당 하나다(`ProjectAction.HANDOVER`). 집합이 지금 `canAssign`과
 * 같지만 뜻이 다르므로, 한쪽이 바뀔 때 다른 쪽이 조용히 따라가면 안 된다.
 */
export function canHandover(me: MeView | null, detail: ProjectDetail | null): boolean {
  return myRole(me, detail) === 'PM'
}
