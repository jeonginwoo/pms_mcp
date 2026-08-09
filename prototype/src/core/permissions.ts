// 권한 판정 — 상위 PRD §4가 원본. canDo = orgPerm OR projectPerm(프로젝트별 매트릭스)
// orgPerm은 권한 그룹(RoleGroup) 플래그로 판정 (피드백 2차 #2 — 그룹 편집 가능)
import type { PermAction, Person, PermissionOverride, Project, ProjectRole, Assignment, RoleGroup } from '../types'

const FALLBACK: RoleGroup = { key: '?', name: '(미지정)', scope: 'SELF', createProject: false, manageContract: false, manageOrg: false, adminAll: false }
export function groupOf(user: Person, groups: RoleGroup[]): RoleGroup {
  return groups.find((g) => g.key === user.orgRole) ?? FALLBACK
}

// §4-2 기본값 표. 조회/삭제/이관은 커스텀 불가(고정) — 별도 상수로 둔다.
export type MatrixAction = PermAction // 조정 가능 4종
export const ACTION_LABEL: Record<PermAction, string> = {
  EDIT_INFO: '프로젝트 정보 수정',
  ASSIGN: '인력 배정 / M/M 입력',
  PROGRESS: '진척률 수정',
  COMPLETE_REOPEN: '완료 처리 · 재개',
}

const DEFAULT_MATRIX: Record<ProjectRole, Record<PermAction, boolean>> = {
  PM: { EDIT_INFO: true, ASSIGN: true, PROGRESS: true, COMPLETE_REOPEN: true },
  PL: { EDIT_INFO: true, ASSIGN: false, PROGRESS: true, COMPLETE_REOPEN: true },
  PARTICIPANT: { EDIT_INFO: false, ASSIGN: false, PROGRESS: true, COMPLETE_REOPEN: true },
}

// 고정 행위(커스텀 불가): 조회=배정 전원 O · 삭제/이관=PM만
export function roleOf(user: Person, project: Project, assignments: Assignment[], groups: RoleGroup[]): ProjectRole | null {
  if (groupOf(user, groups).adminAll) return 'PM' // §4-1 관리자 치환
  const a = assignments.find(
    (x) => x.projectId === project.id && x.personId === user.id && x.status === 'ACTIVE',
  )
  return a ? a.role : null
}

export function effectiveCell(
  overrides: PermissionOverride[], projectId: number, role: ProjectRole, action: PermAction,
): boolean {
  if (role === 'PM') return true // PM 열 고정
  const o = overrides.find(
    (x) => x.projectId === projectId && x.role === role && x.action === action,
  )
  return o ? o.allowed : DEFAULT_MATRIX[role][action]
}

export function defaultCell(role: ProjectRole, action: PermAction): boolean {
  return DEFAULT_MATRIX[role][action]
}

/** 프로젝트 범위 행위 판정 (조정 가능 4종) */
export function canDo(
  user: Person, project: Project, action: PermAction,
  assignments: Assignment[], overrides: PermissionOverride[], groups: RoleGroup[],
): boolean {
  const role = roleOf(user, project, assignments, groups)
  if (!role) return false
  return effectiveCell(overrides, project.id, role, action)
}

/** 고정 행위: 삭제·유지보수 이관 = PM만(관리자 치환 포함) */
export function canDoFixed(user: Person, project: Project, assignments: Assignment[], groups: RoleGroup[]): boolean {
  return roleOf(user, project, assignments, groups) === 'PM'
}

/** 프로젝트 밖 행위 — 권한 그룹 플래그 판정 (상위 PRD §4-3 대응) */
export function orgCanCreateProject(user: Person, groups: RoleGroup[]): boolean {
  return groupOf(user, groups).createProject
}
export function orgCanManageContract(user: Person, groups: RoleGroup[]): boolean {
  return groupOf(user, groups).manageContract
}
export function orgIsAdmin(user: Person, groups: RoleGroup[]): boolean {
  return groupOf(user, groups).manageOrg
}

export const SCOPE_LABEL: Record<RoleGroup['scope'], string> = {
  ALL: '전사', DIVISION: '자기 부문', TEAM: '자기 팀(하위 포함)', SELF: '본인 참여',
}
