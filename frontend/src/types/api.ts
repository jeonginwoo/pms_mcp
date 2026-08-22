/*
 * 서버 DTO 타입 — 이름은 백엔드 DTO와 일치시킨다(Ubiquitous Language, conventions §2).
 * 열거 값은 서버가 이름(name)으로 직렬화한다 — 한국어 표기는 labels.ts가 갖는다.
 */

export type ProjectStatus =
  | 'CONTRACT_PENDING'
  | 'ORDER_CONFIRMED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'UNDER_MAINTENANCE'

/** status 파생 그룹 — 유지보수중은 어디에도 들지 않아 null이다 (PRD-pms §5) */
export type ProjectPhase = 'SALES' | 'SOLUTION'

export type Engagement = 'REMOTE' | 'ONSITE' | 'PARTIAL_ONSITE'

export type ProjectRole = 'PM' | 'PL' | 'PARTICIPANT'

/** GET /api/people — 인원 참조 값 */
export interface PersonRef {
  id: number
  name: string
  orgUnit: string
  grade: string
}

/**
 * GET /api/me — 화자의 신원 + 권한 그룹 플래그.
 * 버튼 노출 판단에만 쓴다 — 최종 판정은 서버다(상위 PRD §4-1).
 */
export interface MeView {
  id: number
  name: string
  orgUnit: string
  grade: string
  group: string
  visibilityScope: 'COMPANY' | 'DIVISION' | 'TEAM' | 'SELF'
  createProject: boolean
  manageContracts: boolean
  manageAllProjects: boolean
  manageOrg: boolean
}

/** 직급·권한 그룹 선택 목록 (관리 화면 전용) */
export interface ReferenceItem {
  id: number
  name: string
}

/** POST /api/people — 등록 시 로그인 계정도 함께 만들어진다 (초기 비밀번호는 서버 규칙) */
export interface CreatePersonBody {
  name: string
  orgUnitId: number
  gradeId: number
  groupId: number
  email: string
}

/** POST /api/org-units — parentId가 null이면 회사(root) 생성 요청이다 */
export interface CreateOrgUnitBody {
  parentId: number | null
  name: string
}

/** GET /api/org-units — 조직 노드 (deletable은 서버가 판정한 E3-3 결과) */
export interface OrgUnitView {
  id: number
  parentId: number | null
  name: string
  memberCount: number
  childCount: number
  deletable: boolean
}

/** GET /api/projects 항목 — 목록은 상세보다 필드가 적다(기간·솔루션 없음) */
export interface ProjectSummary {
  id: number
  client: string
  name: string
  status: ProjectStatus
  progress: number
  managerId: number
  managerName: string | null
}

/** 프로젝트 상세의 배정 항목 */
export interface AssignmentView {
  id: number
  personId: number
  personName: string | null
  role: ProjectRole
  startDate: string | null
  endDate: string | null
  monthlyMm: number
  version: number
}

/** GET /api/projects/{id} */
export interface ProjectDetail {
  id: number
  client: string
  name: string
  solution: string | null
  engagement: Engagement
  status: ProjectStatus
  phase: ProjectPhase | null
  progress: number
  contractMm: number
  startDate: string | null
  endDate: string | null
  managerId: number
  version: number
  assignments: AssignmentView[]
}

/** PUT /api/projects/{id}/progress 응답 — 2단계 확인 프로토콜 (AC A2-1·A2-2) */
export interface ProgressUpdateResult {
  projectId: number
  name: string
  currentProgress: number
  requestedProgress: number
  committed: boolean
  completable: boolean
  version: number
}

/** §7 page 봉투 */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** POST /api/auth/login · /refresh */
export interface TokenResponse {
  accessToken: string
  refreshToken: string
}

/**
 * 공통 응답 봉투 (2026-08-22) — 모든 응답이 `{success, data}` 또는 `{success, error}`다.
 * 예외는 `/api/auth/jwks` 하나(RFC 7517 표준 형태)이고 이 클라이언트는 부르지 않는다.
 */
export interface ApiEnvelope<T> {
  success: boolean
  data?: T
  error?: ApiErrorBody
}

/**
 * 실패 응답 본문. `field`가 **선택**인 이유: 서버가 NON_NULL로 직렬화하므로 필드와
 * 무관한 오류(401·404·500)에서는 키 자체가 오지 않는다. 클라이언트는 이것을 null로
 * 정규화해서 `undefined`라는 세 번째 상태가 화면까지 새지 않게 한다.
 */
export interface ApiErrorBody {
  code: string
  message: string
  field?: string | null
  traceId: string
}

// ── 요청 본문 ──

export interface AssignmentSpecBody {
  personId: number
  role: ProjectRole
  startDate?: string | null
  endDate?: string | null
  monthlyMm?: number
}

export interface CreateProjectBody {
  client: string
  name: string
  solution?: string | null
  engagement: Engagement
  contractMm: number
  startDate?: string | null
  endDate?: string | null
  assignments: AssignmentSpecBody[]
}

export interface EditProjectBody {
  client: string
  name: string
  solution?: string | null
  engagement: Engagement
  contractMm: number
  startDate?: string | null
  endDate?: string | null
  status: ProjectStatus
  version: number
}

export interface UpdateProgressBody {
  progress: number
  version: number
  confirmed: boolean
}

export interface CreateAssignmentBody {
  personId: number
  role: ProjectRole
  startDate?: string | null
  endDate?: string | null
  monthlyMm?: number
}

export interface UpdateAssignmentBody {
  startDate?: string | null
  endDate?: string | null
  monthlyMm: number
  version: number
}
