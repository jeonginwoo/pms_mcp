// 목업 스토어 — 백엔드 없이 API 계약(§7)의 의미론(2단계 확인·403/404/409/422·감사·알림)을 재현한다.
// 목적: 화면·권한·흐름 검증. 실제 구현은 PMS-M0~M6에서 별도(이 코드는 명세가 아니다).
import { useSyncExternalStore } from 'react'
import type {
  AppState_, Assignment, AuditEntry, IssueComment, IssueStatus, MaintenanceContract,
  MaintenanceIssue, MaintenanceSite, Notification, PermAction, PermissionOverride,
  Person, Project, ProjectRole, ProjectStatus, Result, RoleGroup,
} from './state'
import { ok, err } from '../types'
import { buildAssignments, buildGrades, buildOrgUnits, buildPeople, buildProjects, buildRoleGroups } from './db'
import { mockComments, mockContacts, mockContracts, mockIssues, mockSites } from '../data/maintenance'
import { canDo, canDoFixed, defaultCell, groupOf, orgCanCreateProject, orgCanManageContract, orgIsAdmin, roleOf } from './permissions'
import { divisionOfUnit, isProjectVisible, subtreeNames } from './visibility'
import { utilizationFor } from './utilization'

export type AppState = AppState_

function initialState(): AppState {
  const people = buildPeople()
  const projects = buildProjects()
  const assignments = buildAssignments(projects)
  return {
    orgUnits: buildOrgUnits(people),
    roleGroups: buildRoleGroups(),
    grades: buildGrades(people),
    currentUserId: null,
    people,
    projects,
    assignments,
    overrides: [],
    contracts: [...mockContracts],
    sites: [...mockSites],
    contacts: [...mockContacts],
    issues: [...mockIssues],
    comments: [...mockComments],
    notifications: [
      { id: 1, recipientId: 18, type: 'project', refType: 'PROJECT', refId: 313, message: '[근로복지공단 스마트 산재보험] 종료일(D-7)이 다가옵니다.', read: false, createdAt: '2026-08-08 09:00' },
      { id: 2, recipientId: 18, type: 'project', refType: 'ISSUE', refId: 1, message: '[경남은행] 장애 이슈가 접수되었습니다: 검색 결과 간헐적 타임아웃', read: false, createdAt: '2026-08-04 11:20' },
      { id: 3, recipientId: 16, type: 'org', refType: 'PERSON', refId: 17, message: '남민준님의 8월 보정 가동률이 100%를 초과했습니다.', read: true, createdAt: '2026-08-03 08:00' },
    ],
    audit: [
      { id: 1, entityType: 'Project', entityId: 313, action: 'UPDATE', actorId: 18, source: 'WEB', before: '{"progress":55}', after: '{"progress":60}', projectId: 313, at: '2026-08-05 15:22' },
      { id: 2, entityType: 'Project', entityId: 317, action: 'UPDATE', actorId: 13, source: 'MCP', before: '{"progress":70}', after: '{"progress":75}', projectId: 317, at: '2026-08-06 10:41' },
    ],
    seq: 1000,
  }
}

let state: AppState = initialState()
const listeners = new Set<() => void>()

function emit() {
  state = { ...state }
  listeners.forEach((l) => l())
}
function subscribe(l: () => void) {
  listeners.add(l)
  return () => { listeners.delete(l) }
}
export function useApp(): AppState {
  return useSyncExternalStore(subscribe, () => state)
}
export function getState() { return state }
export function resetAll() { state = initialState(); emit() }

const nextId = () => ++state.seq
const now = () => {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
export const CURRENT_MONTH = '2026-08' // 시드 기준일(eval 기준일과 동일)

function me(): Person {
  const u = state.people.find((p) => p.id === state.currentUserId)
  if (!u) throw new Error('로그인 필요')
  return u
}
function audit(entry: Omit<AuditEntry, 'id' | 'at' | 'source'>) {
  state.audit.unshift({ ...entry, id: nextId(), at: now(), source: 'WEB' })
}
function notify(recipientId: number, type: string, refType: string, refId: number, message: string) {
  const r = state.people.find((p) => p.id === recipientId)
  if (!r) return
  const prefKey = (['progress', 'project', 'org', 'weekly'] as const).find((k) => k === type)
  if (prefKey && !r.notifPrefs[prefKey]) return // F1-5
  state.notifications.unshift({
    id: nextId(), recipientId, type, refType, refId, message, read: false, createdAt: now(),
  })
}

function checkOverbooking(personId: number) {
  const person = state.people.find((p) => p.id === personId)
  if (!person || !person.billable) return
  const [row] = utilizationFor(CURRENT_MONTH, [person], state.projects, state.assignments)
  if (row.adjusted > 100) {
    const lead = state.people.find((p) => p.team === person.team && groupOf(p, state.roleGroups).scope === 'TEAM')
    if (lead) notify(lead.id, 'org', 'PERSON', personId, `${person.name}님의 ${CURRENT_MONTH.slice(5)}월 보정 가동률이 100%를 초과했습니다.`)
  }
}

// ── 인증 ────────────────────────────────────────────────
export function login(email: string, password: string): Result {
  const p = state.people.find((x) => x.email === email && x.active)
  if (!p || password !== 'proten1!') return err('UNAUTHENTICATED', '이메일 또는 비밀번호가 올바르지 않습니다.')
  state.currentUserId = p.id
  emit()
  return ok(undefined)
}
export function loginAs(personId: number) { // 프로토타입 전용 — 권한 검증용 빠른 전환
  state.currentUserId = personId
  emit()
}
export function logout() { state.currentUserId = null; emit() }

// ── 프로젝트 ────────────────────────────────────────────
const TRANSITIONS: Record<ProjectStatus, ProjectStatus[]> = {
  계약대기: ['수주확정'], 수주확정: ['진행중'], 진행중: [], 완료: [], 유지보수중: [],
} // 완료·재개·이관은 전용 경로(A5-1)

function normName(s: string) { return s.trim().replace(/\s+/g, ' ').toLowerCase() }

export interface NewProjectInput {
  name: string; client: string; solution: string
  engagement: Project['engagement']; contractMm: number
  startDate: string; endDate: string
  members: { personId: number; role: ProjectRole }[]
}
export function createProject(input: NewProjectInput): Result<number> {
  const u = me()
  if (!orgCanCreateProject(u, state.roleGroups)) return err('FORBIDDEN', '소속 권한 그룹에 프로젝트 생성 권한이 없습니다.')
  const pms = input.members.filter((m) => m.role === 'PM')
  if (pms.length === 0) return err('PM_REQUIRED', 'PM 1명을 반드시 지정해야 합니다.')
  if (pms.length > 1) return err('MULTIPLE_PM', 'PM은 프로젝트당 정확히 1명입니다.')
  if (!input.name.trim() || !input.client.trim()) return err('VALIDATION_ERROR', '이름과 고객사는 필수입니다.')
  const dup = state.projects.find((p) => !p.deleted && p.client === input.client && normName(p.name) === normName(input.name))
  if (dup) return err('DUPLICATE_NAME', '같은 고객사에 동일 이름의 프로젝트가 있습니다.')
  const pm = state.people.find((x) => x.id === pms[0].personId)
  if (!pm) return err('REF_NOT_FOUND', '지정한 PM을 찾을 수 없습니다.')
  const id = nextId()
  const proj: Project = {
    id, name: input.name.trim(), client: input.client, solution: input.solution,
    engagement: input.engagement, contractMm: input.contractMm,
    startDate: input.startDate, endDate: input.endDate,
    status: '계약대기', progress: 0, managerId: pm.id,
    team: pm.team, division: pm.division, deleted: false, version: 1,
    lastEditedBy: u.id, lastEditedAt: now(),
  }
  state.projects.unshift(proj)
  for (const m of input.members) {
    state.assignments.push({
      id: nextId(), projectId: id, personId: m.personId, role: m.role,
      startDate: input.startDate, endDate: input.endDate, monthlyMM: 0, status: 'ACTIVE',
    })
  }
  audit({ entityType: 'Project', entityId: id, action: 'CREATE', actorId: u.id, before: null, after: JSON.stringify({ name: proj.name, status: proj.status }), projectId: id })
  emit()
  return ok(id)
}

function findVisibleProject(id: number): Result<Project> {
  const u = me()
  const p = state.projects.find((x) => x.id === id)
  if (!p || !isProjectVisible(u, p, state.assignments, state)) return err('NOT_FOUND', '프로젝트를 찾을 수 없습니다.') // 404 은닉
  return ok(p)
}

export function updateProject(id: number, patch: Partial<Project>, version: number): Result {
  const u = me()
  const found = findVisibleProject(id)
  if (!found.ok) return found
  const p = found.data
  if (!canDo(u, p, 'EDIT_INFO', state.assignments, state.overrides, state.roleGroups)) return err('FORBIDDEN', '프로젝트 정보 수정 권한이 없습니다. (PM·PL)')
  if (version !== p.version) return err('STALE_VERSION', '다른 사용자가 먼저 수정했습니다. 최신 내용을 불러올까요?')
  if (patch.status && patch.status !== p.status) {
    if (!TRANSITIONS[p.status].includes(patch.status)) {
      return err('INVALID_TRANSITION', `'${p.status}' → '${patch.status}' 전이는 허용되지 않습니다. (완료·재개·이관은 전용 버튼)`)
    }
    audit({ entityType: 'Project', entityId: id, action: 'STATE_CHANGE', actorId: u.id, before: JSON.stringify({ status: p.status }), after: JSON.stringify({ status: patch.status }), projectId: id })
  } else {
    audit({ entityType: 'Project', entityId: id, action: 'UPDATE', actorId: u.id, before: JSON.stringify({ name: p.name }), after: JSON.stringify({ name: patch.name ?? p.name }), projectId: id })
  }
  Object.assign(p, patch, { version: p.version + 1, lastEditedBy: u.id, lastEditedAt: now() })
  emit()
  return ok(undefined)
}

/** 진척률 2단계 — confirmed=false는 요약 미리보기(DB 미변경), true가 커밋 (US-A2) */
export function saveProgress(id: number, progress: number, version: number, confirmed: boolean):
  Result<{ preview?: string; completable?: boolean }> {
  const u = me()
  const found = findVisibleProject(id)
  if (!found.ok) return found
  const p = found.data
  if (!canDo(u, p, 'PROGRESS', state.assignments, state.overrides, state.roleGroups)) {
    return err('FORBIDDEN', '이 프로젝트의 진척률 수정 권한이 없습니다.')
  }
  if (p.status === '완료') return err('PROJECT_COMPLETED', '완료된 프로젝트입니다. 재개 후 수정하세요. (A2-8)')
  if (p.status === '유지보수중') return err('PROJECT_COMPLETED', '유지보수 이관된 프로젝트는 수정할 수 없습니다.')
  if (progress < 0 || progress > 100) return err('VALIDATION_ERROR', '진행률은 0~100 사이여야 합니다.')
  if (!confirmed) {
    return ok({ preview: `[${p.name}] 진행률 ${p.progress}% → ${progress}% 로 변경합니다.` })
  }
  if (version !== p.version) return err('STALE_VERSION', '다른 사용자가 먼저 수정했습니다. 최신 내용을 불러올까요?')
  const before = p.progress
  p.progress = progress
  p.version += 1
  p.lastEditedBy = u.id
  p.lastEditedAt = now()
  if (progress === 100 && !p.progressReachedFullAt) p.progressReachedFullAt = now()
  if (progress < 100) p.progressReachedFullAt = undefined
  audit({ entityType: 'Project', entityId: id, action: 'UPDATE', actorId: u.id, before: JSON.stringify({ progress: before }), after: JSON.stringify({ progress }), projectId: id })
  emit()
  // A2-3: 100%여도 상태는 그대로 — completable 안내만
  return ok({ completable: progress === 100 && p.status === '진행중' })
}

export function completeProject(id: number, version: number): Result {
  const u = me()
  const found = findVisibleProject(id)
  if (!found.ok) return found
  const p = found.data
  if (!canDo(u, p, 'COMPLETE_REOPEN', state.assignments, state.overrides, state.roleGroups)) return err('FORBIDDEN', '완료 처리 권한이 없습니다. (배정 인원)')
  if (p.status !== '진행중') return err('INVALID_TRANSITION', '진행중 상태에서만 완료 처리할 수 있습니다.')
  if (p.progress < 100) return err('PROGRESS_INCOMPLETE', `진행률 100%가 전제입니다. (현재 ${p.progress}%)`)
  if (version !== p.version) return err('STALE_VERSION', '다른 사용자가 먼저 수정했습니다.')
  p.status = '완료'
  p.version += 1
  audit({ entityType: 'Project', entityId: id, action: 'STATE_CHANGE', actorId: u.id, before: '{"status":"진행중"}', after: '{"status":"완료"}', projectId: id })
  notify(p.managerId, 'project', 'PROJECT', id, `[${p.name}] 완료 처리되었습니다. 유지보수 이관을 검토하세요.`)
  emit()
  return ok(undefined)
}

export function reopenProject(id: number, version: number): Result {
  const u = me()
  const found = findVisibleProject(id)
  if (!found.ok) return found
  const p = found.data
  if (!canDo(u, p, 'COMPLETE_REOPEN', state.assignments, state.overrides, state.roleGroups)) return err('FORBIDDEN', '재개 권한이 없습니다. (배정 인원)')
  if (p.status === '유지보수중') return err('INVALID_TRANSITION', '유지보수 이관 후에는 재개할 수 없습니다. (A7-4)')
  if (p.status !== '완료') return err('INVALID_TRANSITION', '완료 상태에서만 재개할 수 있습니다.')
  if (version !== p.version) return err('STALE_VERSION', '다른 사용자가 먼저 수정했습니다.')
  p.status = '진행중'
  p.progress = 90 // A7-3 리셋
  p.progressReachedFullAt = undefined
  p.version += 1
  audit({ entityType: 'Project', entityId: id, action: 'STATE_CHANGE', actorId: u.id, before: '{"status":"완료"}', after: '{"status":"진행중","progress":90}', projectId: id })
  // F3-3: 미읽음 완료 지연 알림 회수
  state.notifications = state.notifications.filter(
    (n) => !(n.refType === 'PROJECT' && n.refId === id && n.type === 'progress' && !n.read),
  )
  emit()
  return ok(undefined)
}

export function deleteProject(id: number): Result {
  const u = me()
  const found = findVisibleProject(id)
  if (!found.ok) return found
  const p = found.data
  if (!canDoFixed(u, p, state.assignments, state.roleGroups)) return err('FORBIDDEN', '삭제는 PM만 가능합니다.')
  p.deleted = true
  audit({ entityType: 'Project', entityId: id, action: 'DELETE', actorId: u.id, before: null, after: null, projectId: id })
  emit()
  return ok(undefined)
}

// ── 역할 지정·교체 (US-A6) ──────────────────────────────
export function changePM(projectId: number, personId: number): Result {
  const u = me()
  const found = findVisibleProject(projectId)
  if (!found.ok) return found
  const p = found.data
  if (roleOf(u, p, state.assignments, state.roleGroups) !== 'PM') return err('FORBIDDEN', 'PM 교체는 현 PM만 가능합니다.')
  const target = state.people.find((x) => x.id === personId)
  if (!target) return err('REF_NOT_FOUND', '대상을 찾을 수 없습니다.')
  const prev = state.assignments.find((a) => a.projectId === projectId && a.role === 'PM' && a.status === 'ACTIVE')
  if (prev) prev.role = 'PARTICIPANT' // A6-4 강등(배정 유지)
  let next = state.assignments.find((a) => a.projectId === projectId && a.personId === personId && a.status === 'ACTIVE')
  if (!next) {
    next = { id: nextId(), projectId, personId, role: 'PM', startDate: now().slice(0, 10), endDate: p.endDate, monthlyMM: 0, status: 'ACTIVE' }
    state.assignments.push(next) // A6-4 미배정이면 배정 생성
  } else {
    next.role = 'PM'
  }
  p.managerId = personId
  p.version += 1
  audit({ entityType: 'Project', entityId: projectId, action: 'UPDATE', actorId: u.id, before: JSON.stringify({ pm: prev?.personId }), after: JSON.stringify({ pm: personId }), projectId })
  emit()
  return ok(undefined)
}

export function setRole(projectId: number, personId: number, role: 'PL' | 'PARTICIPANT'): Result {
  const u = me()
  const found = findVisibleProject(projectId)
  if (!found.ok) return found
  const p = found.data
  if (roleOf(u, p, state.assignments, state.roleGroups) !== 'PM') return err('FORBIDDEN', '역할 지정은 PM만 가능합니다.')
  const target = state.assignments.find((a) => a.projectId === projectId && a.personId === personId && a.status === 'ACTIVE')
  if (target?.role === 'PM') return err('INVALID_ROLE', 'PM 변경은 PM 교체 기능으로만 가능합니다. (A6-7)')
  if (!target) {
    // A6-6: 미배정 인원 PL 지정 시 배정 자동 생성 (기간=지정일~종료일, MM=0)
    state.assignments.push({ id: nextId(), projectId, personId, role, startDate: now().slice(0, 10), endDate: p.endDate, monthlyMM: 0, status: 'ACTIVE' })
  } else {
    target.role = role
  }
  audit({ entityType: 'ProjectAssignment', entityId: personId, action: 'UPDATE', actorId: u.id, before: JSON.stringify({ role: target?.role ?? null }), after: JSON.stringify({ role }), projectId })
  emit()
  return ok(undefined)
}

// ── 배정 (EPIC B) ───────────────────────────────────────
export function addAssignment(projectId: number, personId: number, startDate: string, endDate: string, monthlyMM: number): Result {
  const u = me()
  const found = findVisibleProject(projectId)
  if (!found.ok) return found
  const p = found.data
  if (!canDo(u, p, 'ASSIGN', state.assignments, state.overrides, state.roleGroups)) return err('FORBIDDEN', '인력 배정 권한이 없습니다. (기본값: PM)')
  const dup = state.assignments.find((a) => a.projectId === projectId && a.personId === personId && a.status === 'ACTIVE')
  if (dup) return err('DUPLICATE_ASSIGNMENT', '이미 배정된 인원입니다.')
  state.assignments.push({ id: nextId(), projectId, personId, role: 'PARTICIPANT', startDate, endDate, monthlyMM, status: 'ACTIVE' })
  audit({ entityType: 'ProjectAssignment', entityId: personId, action: 'CREATE', actorId: u.id, before: null, after: JSON.stringify({ personId, monthlyMM }), projectId })
  checkOverbooking(personId)
  emit()
  return ok(undefined)
}

export function updateAssignment(assignmentId: number, monthlyMM: number, endDate: string): Result {
  const u = me()
  const a = state.assignments.find((x) => x.id === assignmentId)
  if (!a) return err('NOT_FOUND', '배정을 찾을 수 없습니다.')
  const found = findVisibleProject(a.projectId)
  if (!found.ok) return found
  if (!canDo(u, found.data, 'ASSIGN', state.assignments, state.overrides, state.roleGroups)) return err('FORBIDDEN', 'M/M 수정 권한이 없습니다. (기본값: PM)')
  const before = { monthlyMM: a.monthlyMM, endDate: a.endDate }
  a.monthlyMM = monthlyMM
  a.endDate = endDate
  audit({ entityType: 'ProjectAssignment', entityId: a.personId, action: 'UPDATE', actorId: u.id, before: JSON.stringify(before), after: JSON.stringify({ monthlyMM, endDate }), projectId: a.projectId })
  checkOverbooking(a.personId)
  emit()
  return ok(undefined)
}

export function closeAssignment(assignmentId: number): Result {
  const u = me()
  const a = state.assignments.find((x) => x.id === assignmentId)
  if (!a) return err('NOT_FOUND', '배정을 찾을 수 없습니다.')
  const found = findVisibleProject(a.projectId)
  if (!found.ok) return found
  if (!canDo(u, found.data, 'ASSIGN', state.assignments, state.overrides, state.roleGroups)) return err('FORBIDDEN', '배정 종료 권한이 없습니다. (기본값: PM)')
  if (a.role === 'PM') return err('INVALID_ROLE', 'PM 배정은 종료할 수 없습니다. PM 교체를 먼저 하세요.')
  a.status = 'CLOSED'
  audit({ entityType: 'ProjectAssignment', entityId: a.personId, action: 'UPDATE', actorId: u.id, before: '{"status":"ACTIVE"}', after: '{"status":"CLOSED"}', projectId: a.projectId })
  emit()
  return ok(undefined)
}

// ── 프로젝트별 권한 커스텀 (US-A8) ──────────────────────
// 배치 저장 — 화면에서 토글을 모두 조정한 뒤 '저장' 한 번에 반영·감사 1건 (피드백 #4).
// A8-2의 PUT {overrides:[...]} 계약과 동일 형태: 기본값과 같은 셀은 저장하지 않는다.
export function savePermissions(
  projectId: number,
  cells: { role: 'PL' | 'PARTICIPANT'; action: PermAction; allowed: boolean }[],
): Result {
  const u = me()
  const found = findVisibleProject(projectId)
  if (!found.ok) return found
  const p = found.data
  if (roleOf(u, p, state.assignments, state.roleGroups) !== 'PM') return err('FORBIDDEN', '권한 조정은 PM만 가능합니다.')
  const snapshot = (list: typeof state.overrides) =>
    JSON.stringify(Object.fromEntries(list.filter((o) => o.projectId === projectId).map((o) => [`${o.role}:${o.action}`, o.allowed])))
  const before = snapshot(state.overrides)
  const changed: string[] = []
  for (const cell of cells) {
    const idx = state.overrides.findIndex((o) => o.projectId === projectId && o.role === cell.role && o.action === cell.action)
    const current = idx >= 0 ? state.overrides[idx].allowed : defaultCell(cell.role, cell.action)
    if (current === cell.allowed) continue
    changed.push(`${cell.role}:${cell.action}=${cell.allowed}`)
    if (cell.allowed === defaultCell(cell.role, cell.action)) {
      state.overrides.splice(idx, 1)
    } else if (idx >= 0) {
      state.overrides[idx] = { projectId, ...cell }
    } else {
      state.overrides.push({ projectId, ...cell })
    }
  }
  if (changed.length === 0) return ok(undefined) // 변경 없음 — 감사도 없음
  p.version += 1
  audit({ entityType: 'ProjectPermission', entityId: projectId, action: 'UPDATE', actorId: u.id, before, after: snapshot(state.overrides), projectId })
  emit()
  return ok(undefined)
}

// ── 유지보수 이관 (US-D1) + 계약/사이트/이슈 (US-D2~D4) ─
export interface HandoverInput {
  contractName: string; counterparty: string
  startDate: string; endDate: string; amount: number
  sites: { customer: string; solution: string; target: '인프라' | '솔루션'; serverSpec: string; engineerId: number }[]
}
export function handover(projectId: number, input: HandoverInput): Result<number> {
  const u = me()
  const found = findVisibleProject(projectId)
  if (!found.ok) return found
  const p = found.data
  if (!canDoFixed(u, p, state.assignments, state.roleGroups)) return err('FORBIDDEN', '유지보수 이관은 PM만 가능합니다.')
  if (p.status !== '완료') return err('NOT_COMPLETED', '완료 상태에서만 이관할 수 있습니다.')
  if (!input.contractName.trim() || !input.counterparty.trim() || !input.startDate || !input.endDate || input.sites.length === 0
    || input.sites.some((s) => !s.customer.trim() || !s.engineerId)) {
    return err('VALIDATION_ERROR', '계약명·계약사·기간·사이트(각 담당 엔지니어 포함) 1개 이상은 필수입니다. (D1-3)')
  }
  // 한 트랜잭션: 계약+사이트 생성 + 상태 전이
  const contractId = nextId()
  state.contracts.unshift({
    id: contractId, sourceProjectId: projectId, counterparty: input.counterparty,
    name: input.contractName, status: '신규', contractDate: now().slice(0, 10),
    startDate: input.startDate, endDate: input.endDate, amount: input.amount,
    monthlyAmount: Math.round(input.amount / 12), salesRepId: null,
    inspectionNote: '', note: `프로젝트 [${p.name}] 이관 생성`, version: 1,
  })
  for (const s of input.sites) {
    state.sites.push({ id: nextId(), contractId, ...s })
  }
  p.status = '유지보수중'
  p.version += 1
  audit({ entityType: 'Project', entityId: projectId, action: 'STATE_CHANGE', actorId: u.id, before: '{"status":"완료"}', after: '{"status":"유지보수중"}', projectId })
  audit({ entityType: 'MaintenanceContract', entityId: contractId, action: 'CREATE', actorId: u.id, before: null, after: JSON.stringify({ name: input.contractName }), projectId: null })
  notify(p.managerId, 'project', 'CONTRACT', contractId, `[${p.name}] 유지보수 이관이 완료되었습니다.`)
  emit()
  return ok(contractId)
}

export function saveContract(input: Omit<MaintenanceContract, 'id' | 'version'> & { id?: number }): Result<number> {
  const u = me()
  if (!orgCanManageContract(u, state.roleGroups)) return err('FORBIDDEN', '소속 권한 그룹에 계약 등록·수정 권한이 없습니다.')
  if (!input.name.trim() || !input.counterparty.trim()) return err('VALIDATION_ERROR', '계약명·계약사는 필수입니다.')
  if (input.id) {
    const c = state.contracts.find((x) => x.id === input.id)
    if (!c) return err('NOT_FOUND', '계약을 찾을 수 없습니다.')
    const before = { name: c.name, status: c.status }
    Object.assign(c, input, { version: c.version + 1 })
    audit({ entityType: 'MaintenanceContract', entityId: c.id, action: 'UPDATE', actorId: u.id, before: JSON.stringify(before), after: JSON.stringify({ name: c.name, status: c.status }), projectId: null })
    emit()
    return ok(c.id)
  }
  const id = nextId()
  state.contracts.unshift({ ...input, id, version: 1 })
  audit({ entityType: 'MaintenanceContract', entityId: id, action: 'CREATE', actorId: u.id, before: null, after: JSON.stringify({ name: input.name }), projectId: null })
  emit()
  return ok(id)
}

export function saveSite(input: Omit<MaintenanceSite, 'id'> & { id?: number }): Result {
  const u = me()
  if (!orgCanManageContract(u, state.roleGroups)) return err('FORBIDDEN', '사이트 등록·수정 권한이 없습니다.')
  if (input.id) {
    const s = state.sites.find((x) => x.id === input.id)
    if (!s) return err('NOT_FOUND', '사이트를 찾을 수 없습니다.')
    Object.assign(s, input)
  } else {
    state.sites.push({ ...input, id: nextId() })
  }
  audit({ entityType: 'MaintenanceSite', entityId: input.id ?? state.seq, action: input.id ? 'UPDATE' : 'CREATE', actorId: u.id, before: null, after: JSON.stringify({ customer: input.customer }), projectId: null })
  emit()
  return ok(undefined)
}

export function createIssue(siteId: number, type: MaintenanceIssue['type'], title: string): Result<number> {
  const u = me() // 이슈 등록은 전사(D3)
  const site = state.sites.find((s) => s.id === siteId)
  if (!site) return err('REF_NOT_FOUND', '사이트를 찾을 수 없습니다.')
  if (!title.trim()) return err('VALIDATION_ERROR', '제목은 필수입니다.')
  const id = nextId()
  state.issues.unshift({
    id, siteId, type, title: title.trim(), status: '접수',
    assigneeId: site.engineerId ?? null, // D3-1 기본 배정 = 사이트 엔지니어
    receivedAt: now().slice(0, 10), completedAt: null, version: 1,
  })
  if (site.engineerId) {
    notify(site.engineerId, 'project', 'ISSUE', id, `[${site.customer}] ${type} 이슈가 접수되었습니다: ${title}`)
  }
  audit({ entityType: 'MaintenanceIssue', entityId: id, action: 'CREATE', actorId: u.id, before: null, after: JSON.stringify({ title, type }), projectId: null })
  emit()
  return ok(id)
}

const ISSUE_FLOW: Record<IssueStatus, IssueStatus[]> = {
  접수: ['처리중'], 처리중: ['고객확인대기', '완료'], 고객확인대기: ['완료', '처리중'], 완료: ['처리중'], // 재개만 역방향
}
export function updateIssue(id: number, patch: { status?: IssueStatus; assigneeId?: number | null }): Result {
  const u = me()
  const issue = state.issues.find((x) => x.id === id)
  if (!issue) return err('NOT_FOUND', '이슈를 찾을 수 없습니다.')
  if (patch.status && patch.status !== issue.status) {
    if (!ISSUE_FLOW[issue.status].includes(patch.status)) {
      return err('INVALID_TRANSITION', `'${issue.status}' → '${patch.status}' 전이는 허용되지 않습니다.`)
    }
    audit({ entityType: 'MaintenanceIssue', entityId: id, action: 'UPDATE', actorId: u.id, before: JSON.stringify({ status: issue.status }), after: JSON.stringify({ status: patch.status }), projectId: null })
    issue.status = patch.status
    issue.completedAt = patch.status === '완료' ? now().slice(0, 10) : null
  }
  if (patch.assigneeId !== undefined && patch.assigneeId !== issue.assigneeId) {
    audit({ entityType: 'MaintenanceIssue', entityId: id, action: 'UPDATE', actorId: u.id, before: JSON.stringify({ assigneeId: issue.assigneeId }), after: JSON.stringify({ assigneeId: patch.assigneeId }), projectId: null })
    issue.assigneeId = patch.assigneeId
    if (patch.assigneeId) {
      const site = state.sites.find((s) => s.id === issue.siteId)
      notify(patch.assigneeId, 'project', 'ISSUE', id, `[${site?.customer ?? ''}] 이슈 담당자로 지정되었습니다: ${issue.title}`)
    }
  }
  issue.version += 1
  emit()
  return ok(undefined)
}

export function addComment(issueId: number, content: string): Result {
  const u = me()
  if (!content.trim()) return err('VALIDATION_ERROR', '내용을 입력하세요.')
  state.comments.push({ id: nextId(), issueId, authorId: u.id, content: content.trim(), createdAt: now() })
  emit() // append-only — 수정·삭제 액션 자체가 없다 (D3-3)
  return ok(undefined)
}

// ── 조직·사용자 관리 (EPIC E) ───────────────────────────
export function savePerson(input: { id?: number; name: string; team: string; grade: string; orgRole: Person['orgRole'] }): Result {
  const u = me()
  if (!orgIsAdmin(u, state.roleGroups)) return err('FORBIDDEN', '사용자 관리 권한이 없습니다. (권한 그룹의 관리 기능)')
  if (!state.roleGroups.some((g) => g.key === input.orgRole)) return err('REF_NOT_FOUND', '없는 권한 그룹입니다.')
  const division = divisionOfUnit(state.orgUnits, input.team)
  const coeff = state.grades.find((g) => g.name === input.grade)?.coeff ?? 1.0
  if (input.id) {
    const p = state.people.find((x) => x.id === input.id)
    if (!p) return err('NOT_FOUND', '대상을 찾을 수 없습니다.')
    if (p.isSystem) return err('FORBIDDEN', '시스템 관리자 계정은 수정할 수 없습니다.')
    const before = { name: p.name, team: p.team, orgRole: p.orgRole }
    Object.assign(p, input, { division, gradeCoeff: coeff })
    audit({ entityType: 'Person', entityId: p.id, action: 'UPDATE', actorId: u.id, before: JSON.stringify(before), after: JSON.stringify({ name: p.name, team: p.team, orgRole: p.orgRole }), projectId: null })
  } else {
    const id = nextId()
    state.people.push({
      id, name: input.name, grade: input.grade, email: `user${id}@proten.co.kr`,
      team: input.team, division, orgRole: input.orgRole,
      gradeCoeff: coeff, billable: true, active: true, phone: '',
      notifPrefs: { progress: true, project: true, org: true, weekly: true },
    })
    audit({ entityType: 'Person', entityId: id, action: 'CREATE', actorId: u.id, before: null, after: JSON.stringify({ name: input.name }), projectId: null })
  }
  emit()
  return ok(undefined)
}

export function deactivatePerson(id: number): Result {
  const u = me()
  if (!orgIsAdmin(u, state.roleGroups)) return err('FORBIDDEN', '사용자 관리 권한이 없습니다. (권한 그룹의 관리 기능)')
  const p = state.people.find((x) => x.id === id)
  if (!p) return err('NOT_FOUND', '대상을 찾을 수 없습니다.')
  if (p.isSystem) return err('FORBIDDEN', '시스템 관리자 계정은 비활성화할 수 없습니다.')
  p.active = false // soft 비활성 — 과거 배정·감사·집계 보존 (E2-3)
  audit({ entityType: 'Person', entityId: id, action: 'DELETE', actorId: u.id, before: null, after: null, projectId: null })
  emit()
  return ok(undefined)
}

// ── 조직 구조(트리)·직급 관리 (피드백 2차 #3 — 기획 결정 후보: 현행 PRD는 설정 편집 탭 미승격) ──
// 인원·프로젝트는 조직을 이름으로 참조하므로, 개명 시 소속 인원·프로젝트의 team/division을 함께 갱신한다.
function syncDivisions() {
  for (const p of state.people) { if (!p.isSystem) p.division = divisionOfUnit(state.orgUnits, p.team) }
  for (const pr of state.projects) pr.division = divisionOfUnit(state.orgUnits, pr.team)
}

export function addOrgUnit(parentId: number, name: string): Result {
  const u = me()
  if (!orgIsAdmin(u, state.roleGroups)) return err('FORBIDDEN', '조직 관리는 ADMIN 전용입니다.')
  if (!name.trim()) return err('VALIDATION_ERROR', '조직 이름은 필수입니다.')
  if (state.orgUnits.some((x) => x.name === name.trim())) return err('DUPLICATE_NAME', '이미 있는 조직 이름입니다.')
  if (!state.orgUnits.some((x) => x.id === parentId)) return err('REF_NOT_FOUND', '상위 조직을 찾을 수 없습니다.')
  state.orgUnits.push({ id: nextId(), name: name.trim(), parentId })
  audit({ entityType: 'OrgUnit', entityId: 0, action: 'CREATE', actorId: u.id, before: null, after: JSON.stringify({ name: name.trim() }), projectId: null })
  emit()
  return ok(undefined)
}

export function renameOrgUnit(id: number, name: string): Result {
  const u = me()
  if (!orgIsAdmin(u, state.roleGroups)) return err('FORBIDDEN', '조직 관리는 ADMIN 전용입니다.')
  const unit = state.orgUnits.find((x) => x.id === id)
  if (!unit) return err('NOT_FOUND', '조직을 찾을 수 없습니다.')
  if (!name.trim()) return err('VALIDATION_ERROR', '조직 이름은 필수입니다.')
  if (state.orgUnits.some((x) => x.name === name.trim() && x.id !== id)) return err('DUPLICATE_NAME', '이미 있는 조직 이름입니다.')
  const oldName = unit.name
  unit.name = name.trim()
  state.people.forEach((p) => { if (p.team === oldName) p.team = unit.name })
  state.projects.forEach((p) => { if (p.team === oldName) p.team = unit.name })
  syncDivisions()
  audit({ entityType: 'OrgUnit', entityId: id, action: 'UPDATE', actorId: u.id, before: JSON.stringify({ name: oldName }), after: JSON.stringify({ name: unit.name }), projectId: null })
  emit()
  return ok(undefined)
}

export function deleteOrgUnit(id: number): Result {
  const u = me()
  if (!orgIsAdmin(u, state.roleGroups)) return err('FORBIDDEN', '조직 관리는 ADMIN 전용입니다.')
  const unit = state.orgUnits.find((x) => x.id === id)
  if (!unit) return err('NOT_FOUND', '조직을 찾을 수 없습니다.')
  if (unit.parentId === null) return err('FORBIDDEN', '회사(최상위)는 삭제할 수 없습니다.')
  if (state.orgUnits.some((x) => x.parentId === id)) return err('VALIDATION_ERROR', '하위 조직이 있어 삭제할 수 없습니다.')
  const members = state.people.filter((p) => p.active && !p.isSystem && p.team === unit.name).length
  const projects = state.projects.filter((p) => !p.deleted && p.team === unit.name).length
  if (members > 0 || projects > 0) {
    return err('VALIDATION_ERROR', `인원 ${members}명 · 프로젝트 ${projects}건이 있어 삭제할 수 없습니다. 먼저 이동하세요.`)
  }
  state.orgUnits = state.orgUnits.filter((x) => x.id !== id)
  audit({ entityType: 'OrgUnit', entityId: id, action: 'DELETE', actorId: u.id, before: JSON.stringify({ name: unit.name }), after: null, projectId: null })
  emit()
  return ok(undefined)
}

// ── 권한 그룹 관리 (피드백 2차 #2 — 기획 결정 후보: 현행 PRD는 orgRole 커스텀 Out of Scope) ──
export function saveRoleGroup(input: {
  key?: string; name: string; scope: RoleGroup['scope']
  createProject: boolean; manageContract: boolean; manageOrg: boolean; adminAll: boolean
}): Result {
  const u = me()
  if (!orgIsAdmin(u, state.roleGroups)) return err('FORBIDDEN', '권한 관리는 ADMIN 전용입니다.')
  if (!input.name.trim()) return err('VALIDATION_ERROR', '그룹 이름은 필수입니다.')
  if (state.roleGroups.some((g) => g.name === input.name.trim() && g.key !== input.key)) {
    return err('DUPLICATE_NAME', '이미 있는 그룹 이름입니다.')
  }
  if (input.key) {
    const g = state.roleGroups.find((x) => x.key === input.key)
    if (!g) return err('NOT_FOUND', '그룹을 찾을 수 없습니다.')
    if (g.system) return err('FORBIDDEN', '관리자 그룹은 수정할 수 없습니다. (자기 잠금 방지)')
    const before = { ...g }
    Object.assign(g, input, { key: g.key })
    audit({ entityType: 'RoleGroup', entityId: 0, action: 'UPDATE', actorId: u.id, before: JSON.stringify({ name: before.name, scope: before.scope }), after: JSON.stringify({ name: g.name, scope: g.scope }), projectId: null })
  } else {
    const key = `G${nextId()}`
    state.roleGroups.push({ ...input, key, name: input.name.trim() })
    audit({ entityType: 'RoleGroup', entityId: 0, action: 'CREATE', actorId: u.id, before: null, after: JSON.stringify({ name: input.name }), projectId: null })
  }
  emit()
  return ok(undefined)
}

export function deleteRoleGroup(key: string): Result {
  const u = me()
  if (!orgIsAdmin(u, state.roleGroups)) return err('FORBIDDEN', '권한 관리는 ADMIN 전용입니다.')
  const g = state.roleGroups.find((x) => x.key === key)
  if (!g) return err('NOT_FOUND', '그룹을 찾을 수 없습니다.')
  if (g.system) return err('FORBIDDEN', '관리자 그룹은 삭제할 수 없습니다.')
  const users = state.people.filter((p) => p.active && p.orgRole === key)
  if (users.length > 0) return err('VALIDATION_ERROR', `이 그룹 인원 ${users.length}명이 있어 삭제할 수 없습니다.`)
  state.roleGroups = state.roleGroups.filter((x) => x.key !== key)
  audit({ entityType: 'RoleGroup', entityId: 0, action: 'DELETE', actorId: u.id, before: JSON.stringify({ name: g.name }), after: null, projectId: null })
  emit()
  return ok(undefined)
}

export function saveGrade(name: string, coeff: number, oldName?: string): Result {
  const u = me()
  if (!orgIsAdmin(u, state.roleGroups)) return err('FORBIDDEN', '직급 관리는 ADMIN 전용입니다.')
  if (!name.trim()) return err('VALIDATION_ERROR', '직급 이름은 필수입니다.')
  if (coeff <= 0) return err('VALIDATION_ERROR', '직급계수는 0보다 커야 합니다.')
  if (state.grades.some((g) => g.name === name && g.name !== oldName)) return err('DUPLICATE_NAME', '이미 있는 직급입니다.')
  if (oldName) {
    const g = state.grades.find((x) => x.name === oldName)
    if (!g) return err('NOT_FOUND', '직급을 찾을 수 없습니다.')
    g.name = name
    g.coeff = coeff
    state.people.forEach((p) => { if (p.grade === oldName) { p.grade = name; p.gradeCoeff = coeff } }) // 보정 가동률에 즉시 반영
    audit({ entityType: 'Grade', entityId: 0, action: 'UPDATE', actorId: u.id, before: JSON.stringify({ name: oldName }), after: JSON.stringify({ name, coeff }), projectId: null })
  } else {
    state.grades.push({ name, coeff })
    audit({ entityType: 'Grade', entityId: 0, action: 'CREATE', actorId: u.id, before: null, after: JSON.stringify({ name, coeff }), projectId: null })
  }
  emit()
  return ok(undefined)
}

export function deleteGrade(name: string): Result {
  const u = me()
  if (!orgIsAdmin(u, state.roleGroups)) return err('FORBIDDEN', '직급 관리는 ADMIN 전용입니다.')
  const users = state.people.filter((p) => p.active && !p.isSystem && p.grade === name)
  if (users.length > 0) return err('VALIDATION_ERROR', `이 직급 인원 ${users.length}명이 있어 삭제할 수 없습니다.`)
  state.grades = state.grades.filter((g) => g.name !== name)
  audit({ entityType: 'Grade', entityId: 0, action: 'DELETE', actorId: u.id, before: JSON.stringify({ name }), after: null, projectId: null })
  emit()
  return ok(undefined)
}

// ── 알림 · 내 계정 ──────────────────────────────────────
export function markRead(notifId: number) {
  const n = state.notifications.find((x) => x.id === notifId)
  if (n) { n.read = true; emit() }
}
export function markAllRead() {
  state.notifications.forEach((n) => { if (n.recipientId === state.currentUserId) n.read = true })
  emit()
}
export function updateProfile(name: string, email: string, phone: string): Result {
  const u = me()
  if (state.people.some((p) => p.id !== u.id && p.email === email)) return err('DUPLICATE_EMAIL', '이미 사용 중인 이메일입니다.')
  const before = { name: u.name, email: u.email }
  u.name = name; u.email = email; u.phone = phone
  audit({ entityType: 'User', entityId: u.id, action: 'UPDATE', actorId: u.id, before: JSON.stringify(before), after: JSON.stringify({ name, email }), projectId: null })
  emit()
  return ok(undefined)
}
export function updateNotifPrefs(prefs: Person['notifPrefs']): Result {
  const u = me()
  u.notifPrefs = { ...prefs }
  emit()
  return ok(undefined)
}
