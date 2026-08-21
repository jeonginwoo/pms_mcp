/*
 * 앱 상태 — 세션·라우팅·서버 데이터 로딩과 쓰기 동작의 단일 지점.
 *
 * 화면은 여기서 받은 동작만 호출한다: 실패는 §7 에러 봉투를 그대로 실은 Result로
 * 돌려주므로(throw 하지 않는다) 각 화면이 code에 맞는 문구를 붙일 수 있고,
 * 낙관적 락 충돌(STALE_VERSION)은 여기서 상세를 다시 읽어 최신 version을 채운다.
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import {
  ApiError,
  api,
  clearSession,
  currentSession,
  login as apiLogin,
  startAsCaller,
} from './api'
import type {
  AssignmentView,
  CreateAssignmentBody,
  CreateOrgUnitBody,
  CreatePersonBody,
  CreateProjectBody,
  EditProjectBody,
  MeView,
  OrgUnitView,
  PersonRef,
  ProgressUpdateResult,
  ProjectDetail,
  ProjectSummary,
  ReferenceItem,
  UpdateAssignmentBody,
} from './types/api'

export type Result<T> = { ok: true; value: T } | { ok: false; error: ApiError }

export type Route = 'home' | 'projects' | 'people'

export type BootPhase = 'anon' | 'loading' | 'ready' | 'error'

const ROSTER_KEY = 'pms.callerRoster'

interface Store {
  phase: BootPhase
  bootError: string | null
  /** token = 로그인 세션 · caller = 로그인 없이 화자 지정(인증 OFF 전용) */
  sessionMode: 'token' | 'caller' | null
  me: MeView | null
  people: PersonRef[]
  /** 개발 모드 화자 전환용 명부 — 지금까지 본 인원의 누적(가시성 축소 후에도 되돌아갈 수 있게) */
  roster: PersonRef[]
  orgUnits: OrgUnitView[]
  /** 인력 등록 폼의 선택 목록 — 관리 권한자만 채워진다 */
  grades: ReferenceItem[]
  permissionGroups: ReferenceItem[]
  projects: ProjectSummary[]
  /** 서버가 알려 준 전체 건수 — 한 번에 받은 수와 다르면 화면이 안내한다 */
  totalProjects: number
  route: Route
  detail: ProjectDetail | null
  toast: string | null
  loginError: string | null
  go: (route: Route) => void
  submitLogin: (email: string, password: string) => Promise<void>
  /** 로그인을 건너뛰고 화자만 지정한다 — 화자 전환에도 같은 동작을 쓴다 */
  enterAsCaller: (personId: number) => Promise<void>
  logout: () => void
  reload: () => Promise<void>
  openProject: (projectId: number) => Promise<void>
  closeProject: () => void
  showToast: (message: string) => void
  createProject: (body: CreateProjectBody) => Promise<Result<ProjectDetail>>
  editProject: (body: EditProjectBody) => Promise<Result<ProjectDetail>>
  saveProgress: (progress: number, confirmed: boolean) => Promise<Result<ProgressUpdateResult>>
  complete: () => Promise<Result<ProjectDetail>>
  reopen: () => Promise<Result<ProjectDetail>>
  changeManager: (personId: number) => Promise<Result<ProjectDetail>>
  deleteProject: () => Promise<Result<null>>
  assign: (body: CreateAssignmentBody) => Promise<Result<AssignmentView>>
  updateAssignment: (assignmentId: number, body: UpdateAssignmentBody) =>
    Promise<Result<AssignmentView>>
  closeAssignment: (assignmentId: number) => Promise<Result<null>>
  createPerson: (body: CreatePersonBody) => Promise<Result<PersonRef>>
  deactivatePerson: (personId: number) => Promise<Result<null>>
  createOrgUnit: (body: CreateOrgUnitBody) => Promise<Result<OrgUnitView>>
  deleteOrgUnit: (orgUnitId: number) => Promise<Result<null>>
}

const StoreContext = createContext<Store | null>(null)

export function useStore(): Store {
  const store = useContext(StoreContext)

  if (!store) {
    throw new Error('StoreProvider 안에서만 사용할 수 있습니다')
  }

  return store
}

export function StoreProvider({ children }: { children: ReactNode }) {
  const [phase, setPhase] = useState<BootPhase>(currentSession() ? 'loading' : 'anon')
  const [bootError, setBootError] = useState<string | null>(null)
  const [sessionMode, setSessionMode] =
    useState<'token' | 'caller' | null>(currentSession()?.mode ?? null)
  const [me, setMe] = useState<MeView | null>(null)
  const [people, setPeople] = useState<PersonRef[]>([])
  const [roster, setRoster] = useState<PersonRef[]>(restoreRoster)
  const [orgUnits, setOrgUnits] = useState<OrgUnitView[]>([])
  const [grades, setGrades] = useState<ReferenceItem[]>([])
  const [permissionGroups, setPermissionGroups] = useState<ReferenceItem[]>([])
  const [projects, setProjects] = useState<ProjectSummary[]>([])
  const [totalProjects, setTotalProjects] = useState(0)
  const [route, setRoute] = useState<Route>('home')
  const [detail, setDetail] = useState<ProjectDetail | null>(null)
  const [toast, setToast] = useState<string | null>(null)
  const [loginError, setLoginError] = useState<string | null>(null)

  const showToast = useCallback((message: string) => {
    setToast(message)
    window.setTimeout(() => setToast((current) => (current === message ? null : current)), 2600)
  }, [])

  /** 로그인 직후·새로고침 후의 초기 적재 — 실패 사유를 화면이 보여 줄 수 있게 남긴다. */
  const reload = useCallback(async () => {
    const session = currentSession()

    if (!session) {
      setPhase('anon')

      return
    }

    setPhase('loading')

    try {
      const [profile, visiblePeople, page] = await Promise.all([
        api.me(),
        api.people(),
        api.projects(),
      ])
      setMe(profile)
      setPeople(visiblePeople)
      setRoster((current) => mergeRoster(current, visiblePeople))
      setProjects(page.content)
      setTotalProjects(page.totalElements)
      // 조직 관리 화면은 관리 권한자만 쓸 수 있다 — 없는 사람에게 403을 만들지 않는다
      if (profile.manageOrg) {
        const [units, gradeList, groupList] = await Promise.all([
          api.orgUnits(),
          api.grades(),
          api.permissionGroups(),
        ])
        setOrgUnits(units)
        setGrades(gradeList)
        setPermissionGroups(groupList)
      } else {
        setOrgUnits([])
        setGrades([])
        setPermissionGroups([])
      }

      setBootError(null)
      setPhase('ready')
    } catch (e) {
      const rejected = e instanceof ApiError
        && (e.status === 401 || (e.status === 404 && session.mode === 'caller'))

      if (rejected) {
        // 화자 지정 세션의 401 = 인증이 켜져 있다는 뜻(헤더는 무시된다),
        // 404 = 그런 personId의 인원이 없다는 뜻 — 둘 다 로그인 화면으로 되돌린다
        setLoginError(session.mode === 'caller'
          ? ((e as ApiError).status === 404
            ? `personId ${session.personId} 인원을 찾을 수 없습니다 (시드 1~43)`
            : '백엔드 인증이 켜져 있어 화자 지정으로는 들어갈 수 없습니다 — 로그인하세요')
          : null)
        clearSession()
        setSessionMode(null)
        setPhase('anon')

        return
      }

      setBootError(e instanceof Error ? e.message : '알 수 없는 오류')
      setPhase('error')
    }
  }, [])

  useEffect(() => {
    if (currentSession()) {
      void reload()
    }
  }, [reload])

  const submitLogin = useCallback(async (email: string, password: string) => {
    setLoginError(null)

    try {
      await apiLogin(email, password)
      setSessionMode('token')
      await reload()
    } catch (e) {
      setLoginError(e instanceof Error ? e.message : '로그인에 실패했습니다')
      setPhase('anon')
    }
  }, [reload])

  /**
   * 로그인 없이 화자만 지정한다 (인증 OFF 전용) — 화자 전환도 같은 경로다.
   * 열려 있던 상세는 닫는다: 새 화자에게는 가시성 밖일 수 있다(그러면 404다).
   */
  const enterAsCaller = useCallback(async (personId: number) => {
    setLoginError(null)
    startAsCaller(personId)
    setSessionMode('caller')
    setDetail(null)
    await reload()
  }, [reload])

  const logout = useCallback(() => {
    clearSession()
    setSessionMode(null)
    setMe(null)
    setPeople([])
    setOrgUnits([])
    setProjects([])
    setDetail(null)
    setRoute('home')
    setLoginError(null)
    setPhase('anon')
  }, [])

  /** 목록만 다시 읽는다 — 쓰기 후 상태·진척률 반영용. */
  const refreshProjects = useCallback(async () => {
    const page = await api.projects()
    setProjects(page.content)
    setTotalProjects(page.totalElements)
  }, [])

  /** 인력·조직 관리 후 다시 읽는다 — 목록에서 빠진 결과가 바로 보여야 한다. */
  const refreshOrganization = useCallback(async () => {
    const [visiblePeople, units] = await Promise.all([
      api.people(),
      me?.manageOrg ? api.orgUnits() : Promise.resolve([] as OrgUnitView[]),
    ])
    setPeople(visiblePeople)
    setOrgUnits(units)
  }, [me?.manageOrg])

  const openProject = useCallback(async (projectId: number) => {
    try {
      setDetail(await api.project(projectId))
      setRoute('projects')
    } catch (e) {
      showToast(e instanceof Error ? e.message : '프로젝트를 열 수 없습니다')
    }
  }, [showToast])

  const closeProject = useCallback(() => setDetail(null), [])

  /**
   * 쓰기 동작 공통 처리 — 성공하면 상세·목록을 서버 값으로 다시 채운다.
   * STALE_VERSION일 때도 상세를 다시 읽는다: 사용자가 최신 값을 보고 재시도할 수
   * 있어야 하고, 그것이 §7이 정한 reload-and-retry의 화면 쪽 몫이다.
   */
  const run = useCallback(async <T,>(
    action: () => Promise<T>,
    options: { detail?: number; refresh?: boolean } = {},
  ): Promise<Result<T>> => {
    try {
      const value = await action()

      if (options.detail !== undefined) {
        setDetail(await api.project(options.detail))
      }

      if (options.refresh !== false) {
        await refreshProjects()
      }

      return { ok: true, value }
    } catch (e) {
      const error = e instanceof ApiError
        ? e
        : new ApiError(0, 'NETWORK', e instanceof Error ? e.message : '요청 실패', null, null)

      if (error.code === 'STALE_VERSION' && options.detail !== undefined) {
        setDetail(await api.project(options.detail).catch(() => null))
      }

      return { ok: false, error }
    }
  }, [refreshProjects])

  /** 인력·조직 쓰기 공통 — 성공하면 두 목록을 다시 읽는다(프로젝트 목록은 무관하다). */
  const runOrganization = useCallback(async <T,>(
      action: () => Promise<T>): Promise<Result<T>> => {
    const result = await run(action, { refresh: false })

    if (result.ok) {
      await refreshOrganization()
    }

    return result
  }, [run, refreshOrganization])

  const value = useMemo<Store>(() => ({
    phase,
    bootError,
    sessionMode,
    me,
    people,
    roster,
    orgUnits,
    grades,
    permissionGroups,
    projects,
    totalProjects,
    route,
    detail,
    toast,
    loginError,
    go: (next) => {
      setRoute(next)
      setDetail(null)
    },
    submitLogin,
    enterAsCaller,
    logout,
    reload,
    openProject,
    closeProject,
    showToast,
    createProject: (body) => run(() => api.createProject(body)),
    editProject: (body) => run(() => api.editProject(requireDetail(detail).id, body),
      { detail: requireDetail(detail).id }),
    // confirmed=false(요약)는 저장하지 않으므로 목록·상세를 다시 읽지 않는다
    saveProgress: (progress, confirmed) => run(
      () => api.updateProgress(requireDetail(detail).id,
        { progress, version: requireDetail(detail).version, confirmed }),
      confirmed ? { detail: requireDetail(detail).id } : { refresh: false }),
    complete: () => run(
      () => api.complete(requireDetail(detail).id, requireDetail(detail).version),
      { detail: requireDetail(detail).id }),
    reopen: () => run(
      () => api.reopen(requireDetail(detail).id, requireDetail(detail).version),
      { detail: requireDetail(detail).id }),
    changeManager: (personId) => run(
      () => api.changeManager(requireDetail(detail).id, personId,
        requireDetail(detail).version),
      { detail: requireDetail(detail).id }),
    deleteProject: async () => {
      const projectId = requireDetail(detail).id
      const result = await run(() => api.deleteProject(projectId))

      if (result.ok) {
        setDetail(null)
      }

      return result
    },
    assign: (body) => run(
      () => api.assign(requireDetail(detail).id, body), { detail: requireDetail(detail).id }),
    updateAssignment: (assignmentId, body) => run(
      () => api.updateAssignment(assignmentId, body), { detail: requireDetail(detail).id }),
    closeAssignment: (assignmentId) => run(
      () => api.closeAssignment(assignmentId), { detail: requireDetail(detail).id }),
    createPerson: (body) => runOrganization(() => api.createPerson(body)),
    deactivatePerson: (personId) => runOrganization(() => api.deactivatePerson(personId)),
    createOrgUnit: (body) => runOrganization(() => api.createOrgUnit(body)),
    deleteOrgUnit: (orgUnitId) => runOrganization(() => api.deleteOrgUnit(orgUnitId)),
  }), [phase, bootError, sessionMode, me, people, roster, orgUnits, grades, permissionGroups,
    projects, totalProjects, route, detail, toast, loginError, submitLogin, enterAsCaller, logout,
    reload, openProject, closeProject, showToast, run, runOrganization])

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>
}

/** 상세가 열려 있어야만 부를 수 있는 동작들의 전제 — 없으면 화면 배선 오류다. */
function requireDetail(detail: ProjectDetail | null): ProjectDetail {
  if (!detail) {
    throw new Error('열려 있는 프로젝트가 없습니다')
  }

  return detail
}

/**
 * 화자 명부는 브라우저에 남긴다 (개발 모드 전용).
 * 이유: 팀원으로 전환하면 보이는 인원이 본인뿐이라 셀렉트가 비어 되돌아갈 수 없다.
 * 관리자로 한 번 들어와 본 명부를 기억해 두면 어느 화자에서든 전환할 수 있다.
 */
function restoreRoster(): PersonRef[] {
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(ROSTER_KEY) ?? '[]')

    return Array.isArray(stored) ? (stored as PersonRef[]) : []
  } catch {
    return []
  }
}

function mergeRoster(current: PersonRef[], loaded: PersonRef[]): PersonRef[] {
  const byId = new Map(current.map((person) => [person.id, person]))
  loaded.forEach((person) => byId.set(person.id, person))
  const merged = [...byId.values()].sort((a, b) => a.id - b.id)
  localStorage.setItem(ROSTER_KEY, JSON.stringify(merged))

  return merged
}
