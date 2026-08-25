/*
 * PMS 백엔드 API 클라이언트 — 구현된 엔드포인트만 노출한다.
 *
 * 화면에서 fetch를 직접 부르지 않는다(conventions §3). 서버 계약은 그대로 쓰고
 * 표시용 변환(한국어 라벨 등)은 labels.ts가 담당한다 — 필드를 여기서 개명하면
 * 서버 DTO와 화면 모델이 갈라진다.
 *
 * 인증 두 모드를 함께 지원한다. 백엔드 `pms.auth.enabled`가
 *  - false(기본): 호출자는 `X-Caller-Person-Id` 헤더 — 토큰은 무시된다
 *  - true       : 호출자는 access 토큰의 subject — 헤더는 무시된다
 * 로그인·갱신은 두 모드에서 모두 열려 있으므로, 로그인해서 받은 토큰의 sub를
 * 헤더로도 실어 보내면 스위치 상태와 무관하게 같은 화면이 동작한다.
 * 헤더를 신뢰하는 상태(false)는 개발용이며 그대로 노출하면 안 된다.
 */
import type {
  AssignmentView,
  AuditRecord,
  CommentBody,
  CommentView,
  ContractBody,
  ContractDetail,
  ContractQuery,
  ContractSummary,
  CreateAssignmentBody,
  CreateOrgUnitBody,
  CreatePersonBody,
  CreateProjectBody,
  EditProjectBody,
  GradeBody,
  GradeDetail,
  HandoverBody,
  IssueBody,
  IssueEditBody,
  IssueQuery,
  IssueView,
  ApiEnvelope,
  MeView,
  MoveOrgUnitBody,
  NotificationPreferences,
  NotificationType,
  NotificationView,
  OrgUnitMoveResult,
  OrgUnitView,
  PageResponse,
  PermissionGroupBody,
  PermissionGroupDetail,
  PersonSummary,
  ProgressUpdateResult,
  ProjectDetail,
  ProjectRole,
  ProjectSummary,
  RenameOrgUnitBody,
  SiteBody,
  SiteView,
  TokenResponse,
  UpdateAssignmentBody,
  UpdatePersonBody,
  UpdateProgressBody,
  UtilizationQuery,
  UtilizationView,
} from './types/api'

const ACCESS_KEY = 'pms.accessToken'
const REFRESH_KEY = 'pms.refreshToken'
const CALLER_KEY = 'pms.callerPersonId'

/**
 * 봉투가 아예 성립하지 않을 때의 코드 — 서버 `ErrorCode`에는 없다.
 * 프록시 오류 페이지나 배포 불일치처럼 **계약 밖 응답**을 화면 코드가 서버 오류와
 * 구분할 수 있어야 해서 클라이언트가 붙인다.
 */
const MALFORMED_RESPONSE = 'MALFORMED_RESPONSE'

/** 공통 봉투의 error를 그대로 담는 예외 — 화면은 code로 분기하고 message를 보여 준다. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly field: string | null
  readonly traceId: string | null

  constructor(status: number, code: string, message: string, field: string | null,
      traceId: string | null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.field = field
    this.traceId = traceId
  }
}

/**
 * 세션 두 갈래.
 * - token : 로그인해서 받은 토큰 쌍 — 인증 ON·OFF 어디서나 동작한다
 * - caller: 로그인 없이 화자 id만 지정 — **인증 OFF에서만** 통한다(헤더 신뢰 모드).
 *           개발·데모용 우회 경로이며, 인증을 켜면 서버가 401로 막는다.
 */
export type Session =
  | { mode: 'token'; accessToken: string; refreshToken: string; personId: number }
  | { mode: 'caller'; personId: number }

let session: Session | null = restoreSession()

function restoreSession(): Session | null {
  const accessToken = localStorage.getItem(ACCESS_KEY)
  const refreshToken = localStorage.getItem(REFRESH_KEY)

  if (accessToken && refreshToken) {
    const personId = subjectOf(accessToken)

    if (personId !== null) {
      return { mode: 'token', accessToken, refreshToken, personId }
    }
  }

  const caller = Number(localStorage.getItem(CALLER_KEY))

  return Number.isFinite(caller) && caller > 0 ? { mode: 'caller', personId: caller } : null
}

/** JWT payload의 sub만 읽는다 — 서명 검증은 서버의 일이고, 여기서는 화자 id만 쓴다. */
function subjectOf(token: string): number | null {
  const payload = token.split('.')[1]

  if (!payload) {
    return null
  }

  try {
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))) as {
      sub?: string
    }
    const personId = Number(json.sub)

    return Number.isFinite(personId) ? personId : null
  } catch {
    return null
  }
}

function store(tokens: TokenResponse): Session {
  const personId = subjectOf(tokens.accessToken)

  if (personId === null) {
    throw new ApiError(500, 'INVALID_TOKEN', '토큰에서 사용자를 읽을 수 없습니다', null, null)
  }

  localStorage.setItem(ACCESS_KEY, tokens.accessToken)
  localStorage.setItem(REFRESH_KEY, tokens.refreshToken)
  localStorage.removeItem(CALLER_KEY)
  session = { mode: 'token', ...tokens, personId }

  return session
}

export function currentSession(): Session | null {
  return session
}

/**
 * 로그인 없이 화자만 지정한다 (인증 OFF 전용 — 개발·데모 경로).
 * 토큰은 지운다: 두 방식이 섞이면 어느 것이 화자인지 화면에서 알 수 없다.
 */
export function startAsCaller(personId: number): Session {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.setItem(CALLER_KEY, String(personId))
  session = { mode: 'caller', personId }

  return session
}

export function clearSession(): void {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(CALLER_KEY)
  session = null
}

export async function login(email: string, password: string): Promise<Session> {
  const tokens = await send<TokenResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  }, false)

  return store(tokens)
}

/**
 * access 토큰이 만료되면 refresh로 한 번 회전한다.
 * 실패하면 세션을 비운다 — 만료된 토큰으로 계속 재시도하면 401이 반복될 뿐이다.
 */
async function rotate(): Promise<boolean> {
  if (session?.mode !== 'token') {
    // 화자 지정(caller) 세션에는 회전할 토큰이 없다 — 401은 인증이 켜졌다는 뜻이다
    return false
  }

  try {
    store(await send<TokenResponse>('/api/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken: session.refreshToken }),
    }, false))

    return true
  } catch {
    clearSession()

    return false
  }
}

async function send<T>(path: string, options: RequestInit, authenticated: boolean): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }

  if (authenticated && session) {
    // 인증 OFF 모드의 호출자 식별 — ON 모드에서는 서버가 이 헤더를 무시한다
    headers['X-Caller-Person-Id'] = String(session.personId)

    if (session.mode === 'token') {
      headers.Authorization = `Bearer ${session.accessToken}`
    }
  }

  const res = await fetch(path, { ...options, headers })
  const text = await res.text()
  const body: unknown = text ? safeJson(text) : null

  if (!res.ok) {
    throw toApiError(res.status, body)
  }

  return unwrap<T>(body)
}

/**
 * 공통 봉투를 검증하고 data만 꺼낸다 (2026-08-22 서버 계약).
 *
 * `success`를 실제로 확인하는 이유: 봉투를 도입한 목적이 "먼저 success를 보고 data나
 * error를 읽는다"는 단일 규칙인데, 그냥 `body.data`를 캐스팅하면 봉투가 아닌 2xx 본문
 * (프록시 오류 페이지, 배포 불일치로 감싸지 않은 응답)이 조용히 `undefined`가 되어
 * 화면 깊숙한 곳에서 터진다. 경계에서 실패시키는 편이 추적 가능하다.
 *
 * 본문이 없는 성공(삭제·읽음 처리)은 data가 없어 undefined가 되는데, 그 호출부는
 * 반환값을 쓰지 않는다 — 여기서 굳이 빈 값을 지어내지 않는다.
 */
function unwrap<T>(body: unknown): T {
  const envelope = body as ApiEnvelope<T> | null

  if (!envelope || envelope.success !== true) {
    throw new ApiError(200, MALFORMED_RESPONSE, '서버 응답 형식이 올바르지 않습니다', null, null)
  }

  return envelope.data as T
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function toApiError(status: number, body: unknown): ApiError {
  const envelope = body as ApiEnvelope<never> | null
  const error = envelope?.error

  if (!error) {
    return new ApiError(status, MALFORMED_RESPONSE, `요청 실패 (${status})`, null, null)
  }

  // field는 서버가 NON_NULL로 빼므로 없을 수 있다 — null로 정규화해 undefined가 새지 않게 한다
  return new ApiError(status, error.code, error.message, error.field ?? null, error.traceId)
}

/** 인증이 필요한 요청 — 401이면 토큰을 한 번 회전한 뒤 재시도한다. */
async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  try {
    return await send<T>(path, options, true)
  } catch (e) {
    if (e instanceof ApiError && e.status === 401 && await rotate()) {
      return send<T>(path, options, true)
    }

    throw e
  }
}

/**
 * 프로젝트 목록 — 서버 페이징을 쓰되 한 번에 받아 화면에서 필터한다.
 *
 * ASSUMPTION: 전사 프로젝트가 시드 기준 382건이라 한 번에 받는 편이 단순하고,
 * 서버에 이름 검색(`?keyword=`)·phase 필터가 아직 없다. 수천 건이 되거나 서버
 * 필터가 생기면 페이지 단위 조회로 되돌린다(그때 이 주석이 트리거다).
 */
const LIST_PAGE_SIZE = 500

/**
 * 유지보수 목록은 서버 페이징을 그대로 쓴다 — 계약 105·이슈 14건이지만 화면이
 * 상태·담당자로 거르므로 필터를 질의로 내려보내는 편이 프로젝트 목록보다 자연스럽다
 * (프로젝트 쪽은 서버에 keyword 필터가 없어 한 번에 받는다 — 위 주석).
 */
const MAINTENANCE_PAGE_SIZE = 200

/**
 * 감사는 **페이지로 끊어 읽는다** — 다른 목록과 다른 점이다.
 * 이력은 쌓이기만 하고(append-only) 줄어들지 않으므로 "한 번에 받아 화면에서 거른다"가
 * 성립하지 않는다. 정렬은 서버가 최신순으로 고정한다(호출자가 뒤집을 수 없다).
 */
const AUDIT_PAGE_SIZE = 50

/**
 * 알림 벨의 드롭다운이 한 번에 보여 주는 수. 페이지네이션을 두지 않는 이유는
 * 알림이 **최근 것만 의미가 있는** 목록이기 때문이다 — 오래된 알림을 거슬러
 * 올라가는 화면은 부록 A에 없다(벨은 "미읽음 수 + 목록 + 읽음 처리"다).
 */
const NOTIFICATION_PAGE_SIZE = 20

/** 값이 있는 것만 싣는다 — 서버는 파라미터의 부재를 "필터 없음"으로 읽는다. */
function queryOf(entries: Record<string, string | number | boolean | null | undefined>): string {
  const params = new URLSearchParams()

  Object.entries(entries).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '' && value !== false) {
      params.set(key, String(value))
    }
  })

  return params.toString()
}

/** 빈 값을 질의에 싣지 않는다 — 서버는 파라미터의 **부재**로 집계/개인 지정을 가른다. */
function utilizationParams({ month, personId, orgUnitId, overbooked }: UtilizationQuery): string {
  const params = new URLSearchParams({ month })

  if (personId != null) {
    params.set('personId', String(personId))
  }

  if (orgUnitId != null) {
    params.set('orgUnitId', String(orgUnitId))
  }

  if (overbooked) {
    params.set('overbooked', 'true')
  }

  return params.toString()
}

export const api = {
  me: () => request<MeView>('/api/me'),
  people: () => request<PersonSummary[]>('/api/people'),
  person: (personId: number) => request<PersonSummary>(`/api/people/${personId}`),
  deactivatePerson: (personId: number) =>
    request<null>(`/api/people/${personId}`, { method: 'DELETE' }),

  createPerson: (body: CreatePersonBody) =>
    request<PersonSummary>('/api/people', { method: 'POST', body: JSON.stringify(body) }),

  /** 인력 수정 (E2-2) — 권한 그룹 부여도 이 경로다. 409 STALE_VERSION이 있다 */
  updatePerson: (personId: number, body: UpdatePersonBody) =>
    request<PersonSummary>(`/api/people/${personId}`,
      { method: 'PUT', body: JSON.stringify(body) }),

  /**
   * 소속 이동 (E1-1) — 진행 중 배정이 있어도 **성공**하고 경고가 함께 온다(E1-2).
   * 그래서 응답이 `PersonSummary`가 아니라 `OrgUnitMoveResult`다.
   */
  movePersonOrgUnit: (personId: number, body: MoveOrgUnitBody) =>
    request<OrgUnitMoveResult>(`/api/people/${personId}/org-unit`,
      { method: 'PUT', body: JSON.stringify(body) }),

  orgUnits: () => request<OrgUnitView[]>('/api/org-units'),
  createOrgUnit: (body: CreateOrgUnitBody) =>
    request<OrgUnitView>('/api/org-units', { method: 'POST', body: JSON.stringify(body) }),
  /** 조직 개명 (E3-2) — 같은 부모 밑 동명은 서버가 막지 않는다(AC에 없다) */
  renameOrgUnit: (orgUnitId: number, body: RenameOrgUnitBody) =>
    request<OrgUnitView>(`/api/org-units/${orgUnitId}`,
      { method: 'PUT', body: JSON.stringify(body) }),
  /** 조직 이동 (E3-5) — 순환·root 이동은 400, 없는 상위 조직은 422다(E3-6) */
  moveOrgUnitParent: (orgUnitId: number, parentId: number) =>
    request<OrgUnitView>(`/api/org-units/${orgUnitId}/parent`,
      { method: 'PUT', body: JSON.stringify({ parentId }) }),
  deleteOrgUnit: (orgUnitId: number) =>
    request<null>(`/api/org-units/${orgUnitId}`, { method: 'DELETE' }),

  // 관리 화면 전용 라우트다 — 플래그가 없으면 서버가 403이다.
  // 인력 등록 폼은 이 목록의 id·이름만 쓰고, 관리 화면이 계수·플래그·인원 수를 쓴다.
  grades: () => request<GradeDetail[]>('/api/grades'),
  createGrade: (body: GradeBody) =>
    request<GradeDetail>('/api/grades', { method: 'POST', body: JSON.stringify(body) }),
  updateGrade: (gradeId: number, body: GradeBody) =>
    request<GradeDetail>(`/api/grades/${gradeId}`,
      { method: 'PUT', body: JSON.stringify(body) }),
  /** 쓰는 인원이 있으면 409 IN_USE (E4-3) */
  deleteGrade: (gradeId: number) =>
    request<null>(`/api/grades/${gradeId}`, { method: 'DELETE' }),

  permissionGroups: () => request<PermissionGroupDetail[]>('/api/permission-groups'),
  createPermissionGroup: (body: PermissionGroupBody) =>
    request<PermissionGroupDetail>('/api/permission-groups',
      { method: 'POST', body: JSON.stringify(body) }),
  /** systemFixed(관리자) 그룹은 422 IMMUTABLE_GROUP — 마지막 관리자의 자기 잠금 방지 */
  updatePermissionGroup: (groupId: number, body: PermissionGroupBody) =>
    request<PermissionGroupDetail>(`/api/permission-groups/${groupId}`,
      { method: 'PUT', body: JSON.stringify(body) }),
  deletePermissionGroup: (groupId: number) =>
    request<null>(`/api/permission-groups/${groupId}`, { method: 'DELETE' }),

  /**
   * 내 알림 (F1-3) — 조회는 언제나 본인 것이라 대상 지정 파라미터가 없다.
   * `read=false`면 미읽음만. SSE(F1-4)는 서버에 아직 없어 목록을 열 때 다시 읽는다.
   */
  notifications: (read?: boolean) =>
    request<PageResponse<NotificationView>>(
      `/api/notifications?${queryOf({ read, size: NOTIFICATION_PAGE_SIZE })}`),
  markNotificationRead: (notificationId: number) =>
    request<null>(`/api/notifications/${notificationId}/read`, { method: 'PATCH' }),

  /** 알림 설정 (H1-4) — 유형 전체가 오고, PUT은 전체 교체다 */
  notifPrefs: () => request<NotificationPreferences>('/api/me/notif-prefs'),
  updateNotifPrefs: (enabled: Record<NotificationType, boolean>) =>
    request<NotificationPreferences>('/api/me/notif-prefs',
      { method: 'PUT', body: JSON.stringify({ enabled }) }),

  projects: () =>
    request<PageResponse<ProjectSummary>>(
      `/api/projects?page=0&size=${LIST_PAGE_SIZE}&sort=id,desc`),
  project: (projectId: number) => request<ProjectDetail>(`/api/projects/${projectId}`),

  createProject: (body: CreateProjectBody) =>
    request<ProjectDetail>('/api/projects', { method: 'POST', body: JSON.stringify(body) }),
  editProject: (projectId: number, body: EditProjectBody) =>
    request<ProjectDetail>(`/api/projects/${projectId}`,
      { method: 'PUT', body: JSON.stringify(body) }),

  updateProgress: (projectId: number, body: UpdateProgressBody) =>
    request<ProgressUpdateResult>(`/api/projects/${projectId}/progress`,
      { method: 'PUT', body: JSON.stringify(body) }),

  complete: (projectId: number, version: number) =>
    request<ProjectDetail>(`/api/projects/${projectId}/complete`,
      { method: 'POST', body: JSON.stringify({ version }) }),
  reopen: (projectId: number, version: number) =>
    request<ProjectDetail>(`/api/projects/${projectId}/reopen`,
      { method: 'POST', body: JSON.stringify({ version }) }),
  /**
   * 유지보수 이관 (D1-1) — 완료 상태에서만, PM만.
   * 계약·사이트 생성과 상태 전이가 서버에서 한 트랜잭션이다: 400·409면
   * 프로젝트는 완료로 남고 계약도 만들어지지 않는다(D1-2·D1-3).
   */
  handover: (projectId: number, body: HandoverBody) =>
    request<ProjectDetail>(`/api/projects/${projectId}/handover`,
      { method: 'POST', body: JSON.stringify(body) }),

  changeManager: (projectId: number, personId: number, version: number) =>
    request<ProjectDetail>(`/api/projects/${projectId}/pm`,
      { method: 'PUT', body: JSON.stringify({ personId, version }) }),
  /**
   * 역할 지정·교체 (A6-3) — PL·참여자만. `version`을 보내지 않는다: 바뀌는 행은
   * 프로젝트가 아니라 배정이다(서버 계약도 `{personId, role}` 둘뿐이다).
   * PM 지정은 위 `changeManager`가 전담한다 — 서버가 role=PM을 422로 거절한다(A6-7).
   */
  changeRole: (projectId: number, personId: number, role: ProjectRole) =>
    request<ProjectDetail>(`/api/projects/${projectId}/roles`,
      { method: 'PUT', body: JSON.stringify({ personId, role }) }),
  deleteProject: (projectId: number) =>
    request<null>(`/api/projects/${projectId}`, { method: 'DELETE' }),

  /**
   * 가동률 (AC C1-1) — page 봉투가 아니라 목록이다. 한 달 가동률은 범위 인원
   * 전체를 한 화면에서 비교하는 값이라 서버가 페이지로 자르지 않는다.
   *
   * `overbooked`는 **서버 필터**로 내려보낸다: 판정 기준(`기본 > 100`)이 서버의
   * 것이고 화면이 다시 판정하면 규칙이 두 곳에 생긴다.
   */
  utilization: (query: UtilizationQuery) =>
    request<UtilizationView[]>(`/api/utilization?${utilizationParams(query)}`),

  /**
   * 유지보수 계약 목록 (D4-1) — keyword는 계약명·계약사·**사이트명** 3종 부분 일치다.
   * 사이트명이 들어 있는 것이 중요하다: 45사이트 계약(가온아이)에 고객사명으로
   * 도달하는 유일한 경로이고, 맞은 사이트는 `matchedSites`로 돌아온다.
   */
  /**
   * 통합 감사 로그 (G1-3) — "사용자/조직/권한 관리" 플래그가 없으면 서버가 403이다.
   * 조직·계정 변경까지 담는 유일한 뷰다(프로젝트 스코프 뷰는 아래).
   */
  audit: (page: number) =>
    request<PageResponse<AuditRecord>>(`/api/audit?${queryOf({ page, size: AUDIT_PAGE_SIZE })}`),

  /** 프로젝트별 이력 (G2-2) — 가시성 밖은 403이 아니라 404다(G2-3 은닉). */
  projectAudit: (projectId: number, page: number) =>
    request<PageResponse<AuditRecord>>(
      `/api/projects/${projectId}/audit?${queryOf({ page, size: AUDIT_PAGE_SIZE })}`),

  maintenanceContracts: (query: ContractQuery = {}) =>
    request<PageResponse<ContractSummary>>(
      `/api/maintenance/contracts?${queryOf({ ...query, size: MAINTENANCE_PAGE_SIZE })}`),
  maintenanceContract: (contractId: number) =>
    request<ContractDetail>(`/api/maintenance/contracts/${contractId}`),

  /** 유지보수 이슈 목록 (D3-4) — `unassigned`는 담당자 없는 건만 (미배정 필터). */
  maintenanceIssues: (query: IssueQuery = {}) =>
    request<PageResponse<IssueView>>(
      `/api/maintenance/issues?${queryOf({ ...query, size: MAINTENANCE_PAGE_SIZE })}`),

  /**
   * 유지보수 쓰기 (US-D2) — "계약 관리" 플래그가 없으면 서버가 403이다.
   * 삭제 라우트가 없는 것은 누락이 아니다: 계약 종료는 상태 `ENDED`로 표현한다(D2-2).
   */
  createContract: (body: ContractBody) =>
    request<ContractDetail>('/api/maintenance/contracts',
      { method: 'POST', body: JSON.stringify(body) }),
  updateContract: (contractId: number, body: ContractBody) =>
    request<ContractDetail>(`/api/maintenance/contracts/${contractId}`,
      { method: 'PUT', body: JSON.stringify(body) }),
  addSite: (contractId: number, body: SiteBody) =>
    request<SiteView>(`/api/maintenance/contracts/${contractId}/sites`,
      { method: 'POST', body: JSON.stringify(body) }),
  /** 연락처는 **전체 교체**다(§7 PUT) — 빼고 보내면 지워진다 */
  updateSite: (siteId: number, body: SiteBody) =>
    request<SiteView>(`/api/maintenance/sites/${siteId}`,
      { method: 'PUT', body: JSON.stringify(body) }),

  /**
   * 이슈 쓰기 (US-D3) — **계약 쓰기와 달리 플래그 판정이 없다**: 로그인 사용자
   * 전체가 등록·처리·코멘트를 할 수 있다(US-D3 대괄호). 그래서 화면도 버튼을
   * 권한으로 감추지 않는다.
   */
  registerIssue: (body: IssueBody) =>
    request<IssueView>('/api/maintenance/issues',
      { method: 'POST', body: JSON.stringify(body) }),
  /** 상태 전이·담당 재배정 (D3-2) — 흐름 밖 전이는 서버가 409로 막는다 */
  processIssue: (issueId: number, body: IssueEditBody) =>
    request<IssueView>(`/api/maintenance/issues/${issueId}`,
      { method: 'PATCH', body: JSON.stringify(body) }),
  /** 코멘트 (D3-3) — append-only라 수정·삭제 경로가 없다 */
  addIssueComment: (issueId: number, body: CommentBody) =>
    request<CommentView>(`/api/maintenance/issues/${issueId}/comments`,
      { method: 'POST', body: JSON.stringify(body) }),

  assign: (projectId: number, body: CreateAssignmentBody) =>
    request<AssignmentView>(`/api/projects/${projectId}/assignments`,
      { method: 'POST', body: JSON.stringify(body) }),
  updateAssignment: (assignmentId: number, body: UpdateAssignmentBody) =>
    request<AssignmentView>(`/api/assignments/${assignmentId}`,
      { method: 'PUT', body: JSON.stringify(body) }),
  closeAssignment: (assignmentId: number) =>
    request<null>(`/api/assignments/${assignmentId}`, { method: 'DELETE' }),
}


/**
 * 알림 SSE 구독 (AC F1-4) — 열려 있는 동안 새 알림이 즉시 흘러온다.
 *
 * **`EventSource`는 헤더를 싣지 못한다**: 그래서 이 라우트만 `?access_token=` 쿼리
 * 파라미터로 인증한다(PRD-pms §7). 토큰 모드면 access 토큰을, 인증 OFF 모드면
 * personId를 나른다 — 서버의 두 리졸버가 그 둘에 대응한다.
 *
 * **재연결을 브라우저에 맡기지 않고 우리가 한다**(2026-08-25 리뷰 후 수정). 이유는
 * 토큰이 URL에 굳어 있기 때문이다: access 토큰 수명은 1시간인데 서버 이미터는 30분마다
 * 끊으므로, 브라우저의 자동 재연결은 **반드시 낡은 토큰으로** 붙는 순간이 온다.
 * 그때 서버가 401을 주면 `EventSource`는 명세상 **영구 실패**하고(비-200은 재연결하지
 * 않는다) 새로고침 전까지 알림이 끊긴다. 그래서 직접 닫고, **그 시점의 세션에서 토큰을
 * 다시 읽어** 새 연결을 만든다 — 갱신된 토큰이 자연히 실린다.
 *
 * **연결될 때마다 `onConnect`가 불린다**: 끊겨 있던 동안의 것은 서버가 재생하지 않으므로
 * (그 설계의 근거는 서버 컨트롤러 주석) 화면이 목록을 다시 읽어 메운다 — AC F1-4의
 * "미연결이면 재연결·재조회 시 반영" 그대로다.
 *
 * @returns 구독을 끊는 함수 — 컴포넌트가 사라질 때 반드시 불러야 한다
 */
export function subscribeNotifications(
    onNotification: (view: NotificationView) => void,
    onConnect: () => void): () => void {
  let source: EventSource | null = null
  let retry: ReturnType<typeof setTimeout> | null = null
  let closed = false
  // 붙자마자 끊기는 상황에서 재연결 폭풍이 되지 않게 물러난다
  let backoffMs = 1000

  const connect = () => {
    if (closed || !session) {
      return
    }

    // 토큰을 **연결할 때마다** 읽는다 — 굳혀 잡으면 갱신된 토큰이 실리지 않는다
    const proof = session.mode === 'token' ? session.accessToken : String(session.personId)
    source = new EventSource(`/api/notifications/stream?access_token=${encodeURIComponent(proof)}`)

    source.addEventListener('open', () => {
      backoffMs = 1000
      onConnect()
    })

    source.addEventListener('notification', (event) => {
      try {
        onNotification(JSON.parse((event as MessageEvent).data) as NotificationView)
      } catch {
        // 파싱 실패는 흘려보낸다 — 정본은 목록이고 다음 조회가 답한다
      }
    })

    source.addEventListener('error', () => {
      // 401이든 네트워크든 여기로 온다. 브라우저의 자동 재연결에 맡기지 않고
      // 닫은 뒤 새 토큰으로 다시 붙는다
      source?.close()
      source = null

      if (!closed) {
        retry = setTimeout(connect, backoffMs)
        backoffMs = Math.min(backoffMs * 2, 60_000)
      }
    })
  }

  connect()

  return () => {
    closed = true

    if (retry !== null) {
      clearTimeout(retry)
    }

    source?.close()
  }
}
