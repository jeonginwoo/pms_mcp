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

/**
 * GET /api/utilization 항목 (AC C1-1·C1-6).
 *
 * `basic`·`adjusted`는 이미 백분율이다(서버가 ×100 해서 준다). 과부하 판정은
 * **언제나 `basic`**이고 `adjusted`는 단가 가중 보조 지표라 판정에 쓰지 않는다
 * (2026-08-10 재정의). 화면이 그 규칙을 뒤집지 않게 임계값을 한 곳에 둔다 —
 * `OVERBOOKED_THRESHOLD`.
 *
 * `team`·`division`을 서버가 함께 주는 것은 소속별로 묶으라는 뜻이다(C1-6) —
 * 없으면 화면이 인원 수만큼 개인 조회를 반복하게 된다.
 */
export interface UtilizationView {
  personId: number
  name: string
  team: string
  division: string
  /** "yyyy-MM" */
  month: string
  assignedMm: number
  availableMm: number
  basic: number
  adjusted: number
}

/** GET /api/utilization 질의 — personId가 있으면 개인 지정, 없으면 집계다(C1-5) */
export interface UtilizationQuery {
  month: string
  personId?: number | null
  orgUnitId?: number | null
  overbooked?: boolean
}

// ── 유지보수 (EPIC D 조회분 — 전사 공개, 가시성 미적용·404 은닉 없음 · AC D4-3) ──

/**
 * 유지보수 열거값은 **질의와 응답의 형태가 다르다**(2026-08-24 실측).
 * 질의 파라미터는 이름으로 바인딩되고(`?status=ACTIVE`), 응답 필드는 한국어 라벨로
 * 온다(`"status": "유지"`). 라벨을 내보내는 것은 의도다 — 같은 값을 MCP 도구가
 * 그대로 쓰고 eval 기대값이 그 문자열에 걸려 있다.
 *
 * 그래서 화면은 **필터에 이름을 보내고 표시는 서버가 준 라벨을 그대로 쓴다.**
 * 라벨 표를 클라이언트에 또 만들지 않는다(정본이 둘이 된다).
 */
export type ContractStatus = 'PLANNED' | 'NEW' | 'ACTIVE' | 'ENDED'

export type IssueStatus = 'RECEIVED' | 'IN_PROGRESS' | 'AWAITING_CLIENT' | 'DONE'

export type IssueType = 'INCIDENT' | 'INQUIRY' | 'REQUEST'

/** GET /api/maintenance/contracts 항목 (D4-1) — matchedSites는 keyword가 맞은 사이트 */
export interface ContractSummary {
  id: number
  contractor: string
  name: string
  /** 한국어 라벨 (예: "유지") */
  status: string
  startDate: string | null
  endDate: string | null
  siteCount: number
  matchedSites: string[]
}

/** 사이트의 연락처 — `raw`는 시트 원문이다(구조화에 실패한 값도 잃지 않는다) */
export interface ContactView {
  id: number
  /** 한국어 라벨 ("계약사" | "고객사") */
  party: string
  name: string | null
  title: string | null
  phone: string | null
  email: string | null
  raw: string | null
}

export interface SiteView {
  id: number
  name: string
  channel: string | null
  serverSpec: string | null
  engineer: PersonRef | null
  contacts: ContactView[]
}

/** GET /api/maintenance/contracts/{id} (D4-2) — sourceProjectId는 이관으로 생긴 계약에만 있다 */
export interface ContractDetail {
  id: number
  sourceProjectId: number | null
  contractor: string
  name: string
  status: string
  sheetSection: string | null
  contractDate: string | null
  contractDateNote: string | null
  startDate: string | null
  endDate: string | null
  amount: number | null
  monthlyAmount: number | null
  salesRep: PersonRef | null
  category: string | null
  targetInfra: string | null
  regularCheck: string | null
  note: string | null
  sites: SiteView[]
  /** 키가 한국어 상태 라벨이다 (예: {"완료": 7}) */
  issueCountByStatus: Record<string, number>
  version: number
}

/** 이슈 코멘트 — append-only다(수정·삭제 API가 없다 · D3-3) */
export interface CommentView {
  id: number
  author: PersonRef | null
  content: string
  createdAt: string
}

/** GET /api/maintenance/issues 항목 (D3-4) — 계약에 연결되지 않은 이슈가 있어 전부 nullable */
export interface IssueView {
  id: number
  /** 한국어 라벨 ("장애" | "문의" | "요청") */
  type: string
  /** 한국어 라벨 ("접수" | "처리중" | "고객확인대기" | "완료") */
  status: string
  title: string
  receivedAt: string | null
  completedAt: string | null
  assignee: PersonRef | null
  siteId: number | null
  siteName: string | null
  contractId: number | null
  contractName: string | null
  comments: CommentView[]
  version: number
}

/** GET /api/maintenance/contracts 질의 (D4-1) */
export interface ContractQuery {
  status?: ContractStatus | null
  contractor?: string | null
  endedBefore?: string | null
  keyword?: string | null
}

/** GET /api/maintenance/issues 질의 (D3-4) — unassigned는 "미배정만" 필터다 */
export interface IssueQuery {
  status?: IssueStatus | null
  type?: IssueType | null
  siteId?: number | null
  assigneeId?: number | null
  contractId?: number | null
  unassigned?: boolean
}

// ── 감사 (EPIC G — 같은 테이블의 두 읽기 뷰다) ──

/** §5 상태 전이는 STATE_CHANGE, 그 밖의 변경은 UPDATE다 (v2.1 정리) */
export type AuditAction = 'CREATE' | 'UPDATE' | 'DELETE' | 'STATE_CHANGE'

/** 어느 입구로 들어온 변경인가 — 서버가 요청 경로로 판정한다(`/mcp` → MCP) */
export type AuditSource = 'WEB' | 'MCP'

/**
 * 감사 행 — `GET /api/audit`(G1-3 통합)와 `GET /api/projects/{id}/audit`(G2-2)가
 * **같은 행**을 다른 필터로 돌려준다. 별도 저장이 없다는 게 설계다(G1-1·G1-2).
 *
 * `before`·`after`는 **바뀐 필드만** 담는다(변경 없으면 행 자체가 없다). 값의 타입이
 * 필드마다 달라 `unknown`이고, 화면은 문자열로 만들어 보여 준다 — `any`는 금지다.
 *
 * `projectId`는 프로젝트 스코프 변경에만 채워진다(G2-1) — 조직·계정 변경은 null이라
 * 통합 로그에만 나온다.
 */
export interface AuditRecord {
  id: number
  entityType: string
  entityId: number | null
  projectId: number | null
  action: AuditAction
  actorId: number
  source: AuditSource
  before: Record<string, unknown> | null
  after: Record<string, unknown> | null
  createdAt: string
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
