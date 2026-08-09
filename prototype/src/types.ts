// 도메인 타입 — PRD-pms v2.4 §4 도메인 모델의 화면 검증용 축약판
// orgRole은 권한 그룹(RoleGroup) key를 가리키는 문자열 — 그룹 신설·편집 가능(피드백 2차 #2, 기획 결정 후보)
export type OrgRole = string
export type ProjectRole = 'PM' | 'PL' | 'PARTICIPANT'
export type ProjectStatus = '계약대기' | '수주확정' | '진행중' | '완료' | '유지보수중'
export type Phase = 'SALES' | 'SOLUTION'
export type PermAction = 'EDIT_INFO' | 'ASSIGN' | 'PROGRESS' | 'COMPLETE_REOPEN'

export interface Person {
  id: number
  name: string
  grade: string
  email: string
  team: string
  division: string
  orgRole: OrgRole
  gradeCoeff: number
  billable: boolean
  active: boolean
  isSystem?: boolean // 회사 고유 시스템 계정(삭제 불가) — 인력·가동률·배정 목록에서 제외
  phone?: string
  notifPrefs: { progress: boolean; project: boolean; org: boolean; weekly: boolean }
}

export interface Grade { name: string; coeff: number }

// 조직 구조 — 회사(root)부터 임의 깊이 트리 (피드백 2차 #3)
export interface OrgUnit {
  id: number
  name: string // 인원(team)·프로젝트(team)가 이름으로 연결되므로 전역 유일
  parentId: number | null // null = 회사(root)
}

// 권한 그룹 — orgRole의 정의부. 가시성 범위 + 프로젝트 밖 기능 플래그 (피드백 2차 #2)
export type VisibilityScope = 'ALL' | 'DIVISION' | 'TEAM' | 'SELF'
export interface RoleGroup {
  key: string // Person.orgRole이 참조
  name: string
  scope: VisibilityScope
  createProject: boolean // 프로젝트 생성 (§4-3)
  manageContract: boolean // 유지보수 계약·사이트 등록/수정 (§4-3)
  manageOrg: boolean // 사용자·조직·직급·권한 관리 (설정 화면)
  adminAll: boolean // 모든 프로젝트에서 PM 간주 (§4-1 ADMIN 치환)
  system?: boolean // 관리자 그룹 — 편집·삭제 불가(자기 잠금 방지)
}

// 수행형태 — 원격·상주·부분상주 3종 (피드백 2차 #1: OFFSITE 제거, 시드 32건은 원격으로 흡수)
export const ENGAGEMENT_LABEL: Record<Project['engagement'], string> = {
  REMOTE: '원격', ONSITE: '상주', PARTIAL_ONSITE: '부분상주',
}

export interface Project {
  id: number
  name: string
  client: string
  status: ProjectStatus
  progress: number
  startDate: string
  endDate: string
  contractMm: number
  engagement: 'REMOTE' | 'PARTIAL_ONSITE' | 'ONSITE'
  solution: string
  managerId: number
  team: string
  division: string
  deleted: boolean
  version: number
  lastEditedBy?: number
  lastEditedAt?: string
  progressReachedFullAt?: string // F3 완료 지연 리마인드용
}

export interface Assignment {
  id: number
  projectId: number
  personId: number
  role: ProjectRole
  startDate: string
  endDate: string
  monthlyMM: number
  status: 'ACTIVE' | 'CLOSED'
}

export interface PermissionOverride {
  projectId: number
  role: 'PL' | 'PARTICIPANT'
  action: PermAction
  allowed: boolean
}

// 유지보수 3층 (v2.4)
export type ContractStatus = '예정' | '신규' | '유지' | '종료'
export interface MaintenanceContract {
  id: number
  sourceProjectId: number | null // null = 직접 등록(OEM 등)
  counterparty: string // 계약사
  name: string
  status: ContractStatus
  contractDate: string
  startDate: string
  endDate: string
  amount: number // 연 계약금액(원)
  monthlyAmount: number
  salesRepId: number | null
  inspectionNote: string // 정기점검 — 정보 텍스트만(모델링 미채택)
  note: string
  version: number
}
export interface MaintenanceSite {
  id: number
  contractId: number
  customer: string // 고객사명
  solution: string // 솔루션/버전
  target: '인프라' | '솔루션'
  serverSpec: string
  engineerId: number // 담당 엔지니어 정본
}
export interface MaintenanceContact {
  id: number
  siteId: number
  kind: '계약사' | '고객사'
  name: string
  title: string
  phone: string
  email: string
}
export type IssueType = '장애' | '문의' | '요청'
export type IssueStatus = '접수' | '처리중' | '고객확인대기' | '완료'
export interface MaintenanceIssue {
  id: number
  siteId: number
  type: IssueType
  title: string
  status: IssueStatus
  assigneeId: number | null
  receivedAt: string
  completedAt: string | null
  version: number
}
export interface IssueComment {
  id: number
  issueId: number
  authorId: number
  content: string
  createdAt: string
}

export interface Notification {
  id: number
  recipientId: number
  type: string
  refType: string
  refId: number
  message: string
  read: boolean
  createdAt: string
}

export interface AuditEntry {
  id: number
  entityType: string
  entityId: number
  action: 'CREATE' | 'UPDATE' | 'DELETE' | 'STATE_CHANGE'
  actorId: number
  source: 'WEB' | 'MCP'
  before: string | null
  after: string | null
  projectId: number | null
  at: string
}

// 목업 API 결과 봉투 — 실제 API의 에러 의미론(§7)을 화면에서 재현하기 위함
export type Ok<T = undefined> = { ok: true; data: T }
export type Err = { ok: false; code: string; message: string }
export type Result<T = undefined> = Ok<T> | Err

export const ok = <T,>(data: T): Ok<T> => ({ ok: true, data })
export const err = (code: string, message: string): Err => ({ ok: false, code, message })
