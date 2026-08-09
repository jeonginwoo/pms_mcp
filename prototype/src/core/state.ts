// 앱 상태 형태 정의 + 도메인 타입 재수출 (store.ts에서 사용)
import type {
  Assignment, AuditEntry, Grade, IssueComment, MaintenanceContact, MaintenanceContract,
  MaintenanceIssue, MaintenanceSite, Notification, OrgUnit, PermissionOverride, Person,
  Project, ProjectStatus, Phase, RoleGroup,
} from '../types'

export type * from '../types'

export interface AppState_ {
  currentUserId: number | null
  people: Person[]
  projects: Project[]
  assignments: Assignment[]
  overrides: PermissionOverride[]
  contracts: MaintenanceContract[]
  sites: MaintenanceSite[]
  contacts: MaintenanceContact[]
  issues: MaintenanceIssue[]
  comments: IssueComment[]
  notifications: Notification[]
  audit: AuditEntry[]
  orgUnits: OrgUnit[]
  roleGroups: RoleGroup[]
  grades: Grade[]
  seq: number
}

// phase = status 파생 (v2.4 — 저장 컬럼 아님, 단일 정의)
export function phaseOf(status: ProjectStatus): Phase | null {
  if (status === '계약대기' || status === '수주확정') return 'SALES'
  if (status === '진행중' || status === '완료') return 'SOLUTION'
  return null // 유지보수중 — 화면 노출은 연결된 계약이 담당 (§5)
}
