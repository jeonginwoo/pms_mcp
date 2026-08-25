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

/** 인원 참조 값 — 프로젝트 배정·유지보수 담당자처럼 "누구"만 필요한 자리 */
export interface PersonRef {
  id: number
  name: string
  orgUnit: string
  grade: string
}

/**
 * GET /api/people — 인력 화면이 쓰는 인원 1행 (2026-08-24).
 *
 * `PersonRef`를 넓힌 것이 아니라 **서버가 화면용으로 따로 내주는 값**이다: 서버의
 * `PersonRef`는 `/mcp` 도구도 쓰는 모듈 루트 계약이라 편집용 id·version이 실리면
 * 도구 응답에도 나간다. 여기에 이름·id가 함께 있는 이유는 `PUT /api/people/{id}`가
 * id와 version을 요구하는데 §7에 인원 상세 라우트가 따로 없기 때문이다.
 *
 * 권한 그룹은 **id만** 온다 — 이름은 관리자만 부를 수 있는 `/api/permission-groups`가
 * 해석한다(가시성만으로 남의 권한 등급이 읽히지 않게).
 */
export interface PersonSummary extends PersonRef {
  division: string
  orgUnitId: number
  gradeId: number
  groupId: number
  version: number
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

/**
 * GET /api/grades — 직급 1행 (US-E4. 관리 플래그 전용 라우트다).
 * 인력 등록 폼은 `id`·`name`만 쓰고 관리 화면이 나머지를 쓴다.
 */
export interface GradeDetail {
  id: number
  name: string
  /** 보정 가동률 가중치 — 바꾸면 다음 조회부터 반영된다(캐시 없음, E4-2) */
  coeff: number
  /** 이 직급을 쓰는 인원 수 — **비활성 포함**(서버의 409 IN_USE 판정과 같은 기준) */
  memberCount: number
  version: number
}

/** GET /api/permission-groups — 권한 그룹 1행 (US-E5 · 상위 PRD §4-3) */
export interface PermissionGroupDetail {
  id: number
  name: string
  visibilityScope: VisibilityScope
  createProject: boolean
  manageContracts: boolean
  manageAllProjects: boolean
  manageOrg: boolean
  /** 시스템 고정(관리자) — 수정·삭제가 422다. 화면은 버튼을 잠근다 */
  systemFixed: boolean
  memberCount: number
  version: number
}

export type VisibilityScope = 'COMPANY' | 'DIVISION' | 'TEAM' | 'SELF'

// ── 알림 (EPIC F · H1-4) ──

/** 알림 유형 — 설정(H1-4)이 켜고 끄는 단위이기도 하다 */
export type NotificationType =
  | 'ASSIGNED'
  | 'OVERBOOKED'
  | 'PROJECT_COMPLETED'
  | 'DEADLINE_NEAR'
  | 'COMPLETION_OVERDUE'
  | 'ISSUE_ASSIGNED'

/**
 * GET /api/notifications 항목 (F1-3).
 *
 * `refType`·`refId`로 대상에 이동한다 — 메시지 문자열에서 id를 파싱하면 문구가
 * 바뀌는 순간 깨진다(서버 DTO 주석과 같은 이유).
 */
export interface NotificationView {
  id: number
  type: NotificationType
  refType: string
  refId: number | null
  message: string
  read: boolean
  createdAt: string
}

/**
 * GET·PUT /api/me/notif-prefs (H1-4) — 유형 전체를 담는다(끈 것만 false).
 * PUT은 **전체 교체**다: 보내지 않은 유형을 "그대로 둔다"로 읽으면 유형이 늘 때
 * 화면과 서버가 갈린다.
 */
export interface NotificationPreferences {
  enabled: Record<NotificationType, boolean>
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

/**
 * GET /api/projects 항목 — 목록은 상세보다 필드가 적다(기간·솔루션 없음).
 *
 * `phase`는 서버가 status에서 파생해 실어 준다(§5) — 화면이 status → phase 표를
 * 자기가 갖지 않게 하려는 것이다. 유지보수중은 어느 그룹에도 들지 않아 null이다.
 */
export interface ProjectSummary {
  id: number
  client: string
  name: string
  status: ProjectStatus
  phase: ProjectPhase | null
  progress: number
  managerId: number
  managerName: string | null
}

/** 프로젝트 상세의 배정 항목 */
export interface AssignmentView {
  id: number
  personId: number
  personName: string | null
  /** 재직 여부 — 퇴사자의 배정도 상세에 남으므로(B2-1) 화면이 그 사실을 표시한다 */
  personActive: boolean
  role: ProjectRole
  startDate: string | null
  endDate: string | null
  monthlyMm: number
  version: number
}

/** GET /api/projects/{id} */
/** 프로젝트 안에서 역할별로 갈리는 기능 (§4-2 · 서버 `ProjectAction`) */
export type ProjectAction =
  | 'EDIT_INFO' | 'ASSIGN' | 'PROGRESS' | 'COMPLETE_REOPEN' | 'HANDOVER'

/**
 * GET /api/projects/{id}/permissions — 역할×기능 매트릭스 (US-A8).
 *
 * 화면은 이 표를 **자기가 만들지 않는다**: 기본값과 override의 병합은 서버가 하고
 * 여기 오는 `allowed`가 그 결과다. 클라이언트가 §4-2를 다시 적으면 override가 걸린
 * 프로젝트에서 화면과 서버의 답이 갈린다.
 */
export interface ProjectPermissionMatrix {
  projectId: number
  cells: PermissionCell[]
  version: number
}

export interface PermissionCell {
  role: ProjectRole
  action: ProjectAction
  /** 병합 결과 — 지금 이 프로젝트에서 그 역할이 그 기능을 할 수 있는가 */
  allowed: boolean
  /** PM이 조정할 수 있는 칸인가 (§4-2 고정 칸이면 false — 화면은 잠금으로 그린다) */
  editable: boolean
  /** 기본값과 달라 저장된 칸인가 — "커스텀" 뱃지와 기본값 복원 버튼의 근거 */
  overridden: boolean
}

export interface UpdatePermissionsBody {
  overrides: { role: ProjectRole; action: ProjectAction; allowed: boolean }[]
  version: number
}

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
  /** 열거 이름 — 연락처 수정 폼이 되채우는 값 (라벨 표를 클라이언트에 두지 않으려고 서버가 함께 준다) */
  partyCode: "CONTRACTOR" | "CLIENT"
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
  /** 낙관적 락 (D2-4 PUT /sites/{id}) */
  version: number
}

/** GET /api/maintenance/contracts/{id} (D4-2) — sourceProjectId는 이관으로 생긴 계약에만 있다 */
export interface ContractDetail {
  id: number
  sourceProjectId: number | null
  contractor: string
  name: string
  /** 한국어 라벨 (예: "유지") — 표시용 */
  status: string
  /** 열거 이름 (ACTIVE) — 수정 폼이 상태 select를 되채우는 값 (D2-2) */
  statusCode: ContractStatus
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
  /** 열거 이름 — 처리 화면이 다음 상태를 계산하는 값이다(ContractDetail.statusCode 선례) */
  statusCode: IssueStatus
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

/** PUT /api/people/{id} (E2-2) — 권한 그룹 부여도 이 경로다(그룹은 사람의 속성이다) */
export interface UpdatePersonBody {
  name: string
  orgUnitId: number
  gradeId: number
  groupId: number
  version: number
}

/** PUT /api/people/{id}/org-unit (E1-1) — 소속만 옮긴다 */
export interface MoveOrgUnitBody {
  orgUnitId: number
}

/**
 * PUT /api/people/{id}/org-unit 응답 (E1-2).
 * 진행 중 배정이 있어도 **막지 않고** 경고를 함께 준다 — 조직 개편을 시스템이
 * 거부하면 안 되고, 조용히 넘기면 개편하는 사람이 파급을 모른다.
 */
export interface OrgUnitMoveResult {
  person: PersonSummary
  activeAssignments: number
  warning: string | null
}

/** PUT /api/org-units/{id} (E3-2) — 개명. 같은 부모 밑 동명은 서버가 막지 않는다 */
export interface RenameOrgUnitBody {
  name: string
}

/** POST·PUT /api/grades (E4) — version은 수정에서만 의미가 있다 */
export interface GradeBody {
  name: string
  coeff: number
  version: number
}

/** POST·PUT /api/permission-groups (E5) */
export interface PermissionGroupBody {
  name: string
  visibilityScope: VisibilityScope
  createProject: boolean
  manageContracts: boolean
  manageAllProjects: boolean
  manageOrg: boolean
  version: number
}

// ── 유지보수 쓰기 (US-D2 — 2026-08-24) ──

/**
 * POST·PUT /api/maintenance/contracts (D2-1·D2-2).
 * 등록과 수정이 같은 본문이고 차이는 `version` 하나다(서버 `ContractRequest`와 같은 이유).
 * 시트 유래 필드(sheetSection·contractDateNote)는 화면이 채우지 않는다 — 원본 보존용이다.
 */
export interface ContractBody {
  contractor: string
  name: string
  status: ContractStatus
  contractDate?: string | null
  startDate?: string | null
  endDate?: string | null
  amount?: number | null
  monthlyAmount?: number | null
  salesRepId?: number | null
  category?: string | null
  targetInfra?: string | null
  regularCheck?: string | null
  note?: string | null
  /** 수정에서만 보낸다 */
  version?: number | null
}

/** 사이트 연락처 입력 (D2-4) — 원문(raw)은 서버가 조각으로 조립한다 */
export interface ContactBody {
  party: 'CONTRACTOR' | 'CLIENT'
  name?: string | null
  title?: string | null
  phone?: string | null
  email?: string | null
}

/**
 * POST /contracts/{id}/sites · PUT /sites/{id} (D2-4).
 * `contacts`는 **전체 교체**다(§7 PUT 의미론) — 빼고 보내면 지워진다.
 */
export interface SiteBody {
  name: string
  channel?: 'OEM' | 'ENT' | null
  serverSpec?: string | null
  engineerId?: number | null
  contacts: ContactBody[]
  /** 수정에서만 보낸다 */
  version?: number | null
}

/**
 * POST /api/maintenance/issues (D3-1) — 서버가 정하는 칸은 보내지 않는다.
 * 담당자는 사이트의 담당 엔지니어, 상태는 `접수`, 접수일은 오늘이다.
 */
export interface IssueBody {
  siteId: number
  type: IssueType
  title: string
}

/**
 * PATCH /api/maintenance/issues/{id} (D3-2) — 상태 전이·담당 재배정.
 *
 * **비운 칸은 "그대로 둔다"다**(PATCH 의미론). 그래서 담당 해제는 표현할 수 없다 —
 * `null`이 이미 다른 뜻을 쓰고 있고 AC에 해제 요구가 없다(서버 DTO 주석과 같은 근거).
 */
export interface IssueEditBody {
  status?: IssueStatus | null
  assigneeId?: number | null
  version: number
}

/** POST /api/maintenance/issues/{id}/comments (D3-3) — append-only라 version이 없다 */
export interface CommentBody {
  content: string
}

/**
 * POST /api/projects/{id}/handover (D1-1) — 계약 필수 정보 + 사이트 1개 이상.
 *
 * 계약 상태 칸이 없는 것은 누락이 아니다: 이관 계약은 `유지`로 시작한다(서버가 정한다).
 * 담당 엔지니어는 **사이트마다 필수**다 — 없으면 그 사이트의 이슈가 영원히 미배정으로
 * 남는다(D3-1이 사이트에서 담당을 가져온다).
 */
export interface HandoverBody {
  contractor: string
  name: string
  startDate?: string | null
  endDate?: string | null
  amount?: number | null
  monthlyAmount?: number | null
  sites: HandoverSiteBody[]
  version: number
}

export interface HandoverSiteBody {
  name: string
  engineerId: number
}

/** GET /api/me/account (H1-1) — 수정 폼을 되채우는 값. 알림 설정은 별 라우트다(H1-4) */
export interface AccountView {
  id: number
  name: string
  email: string | null
  phone: string | null
}

/** PUT /api/me/profile (H1-2) — 소속·직급·권한 그룹 칸이 없는 것은 의도다(E2-2의 몫) */
export interface UpdateProfileBody {
  name: string
  email: string
  phone?: string | null
}

/** PUT /api/me/password (H1-3) — 불일치·형식 오류가 같은 400이다 */
export interface ChangePasswordBody {
  current: string
  newPassword: string
}
