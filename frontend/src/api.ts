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
  CreateAssignmentBody,
  CreateOrgUnitBody,
  CreatePersonBody,
  CreateProjectBody,
  EditProjectBody,
  ErrorEnvelope,
  MeView,
  OrgUnitView,
  PageResponse,
  PersonRef,
  ProgressUpdateResult,
  ProjectDetail,
  ProjectSummary,
  ReferenceItem,
  TokenResponse,
  UpdateAssignmentBody,
  UpdateProgressBody,
} from './types/api'

const ACCESS_KEY = 'pms.accessToken'
const REFRESH_KEY = 'pms.refreshToken'
const CALLER_KEY = 'pms.callerPersonId'

/** §7 에러 봉투를 그대로 담는 예외 — 화면은 code로 분기하고 message를 보여 준다. */
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

  return body as T
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function toApiError(status: number, body: unknown): ApiError {
  const envelope = body as Partial<ErrorEnvelope> | null
  const error = envelope?.error

  if (!error) {
    return new ApiError(status, 'UNKNOWN', `요청 실패 (${status})`, null, null)
  }

  return new ApiError(status, error.code, error.message, error.field, error.traceId)
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

export const api = {
  me: () => request<MeView>('/api/me'),
  people: () => request<PersonRef[]>('/api/people'),
  person: (personId: number) => request<PersonRef>(`/api/people/${personId}`),
  deactivatePerson: (personId: number) =>
    request<null>(`/api/people/${personId}`, { method: 'DELETE' }),

  createPerson: (body: CreatePersonBody) =>
    request<PersonRef>('/api/people', { method: 'POST', body: JSON.stringify(body) }),

  orgUnits: () => request<OrgUnitView[]>('/api/org-units'),
  createOrgUnit: (body: CreateOrgUnitBody) =>
    request<OrgUnitView>('/api/org-units', { method: 'POST', body: JSON.stringify(body) }),
  deleteOrgUnit: (orgUnitId: number) =>
    request<null>(`/api/org-units/${orgUnitId}`, { method: 'DELETE' }),

  // 인력 등록 폼의 선택 목록 — 관리 화면 전용이라 같은 판정을 거친다
  grades: () => request<ReferenceItem[]>('/api/grades'),
  permissionGroups: () => request<ReferenceItem[]>('/api/permission-groups'),

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

  changeManager: (projectId: number, personId: number, version: number) =>
    request<ProjectDetail>(`/api/projects/${projectId}/pm`,
      { method: 'PUT', body: JSON.stringify({ personId, version }) }),
  deleteProject: (projectId: number) =>
    request<null>(`/api/projects/${projectId}`, { method: 'DELETE' }),

  assign: (projectId: number, body: CreateAssignmentBody) =>
    request<AssignmentView>(`/api/projects/${projectId}/assignments`,
      { method: 'POST', body: JSON.stringify(body) }),
  updateAssignment: (assignmentId: number, body: UpdateAssignmentBody) =>
    request<AssignmentView>(`/api/assignments/${assignmentId}`,
      { method: 'PUT', body: JSON.stringify(body) }),
  closeAssignment: (assignmentId: number) =>
    request<null>(`/api/assignments/${assignmentId}`, { method: 'DELETE' }),
}
