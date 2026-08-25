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
  subscribeNotifications,
} from './api'
import type {
  AccountView,
  AssignmentView,
  AuditRecord,
  ChangePasswordBody,
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
  MeView,
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
  SiteBody,
  UpdateProfileBody,
  SiteView,
  UpdateAssignmentBody,
  UpdatePersonBody,
  UtilizationQuery,
  UtilizationView,
} from './types/api'

export type Result<T> = { ok: true; value: T } | { ok: false; error: ApiError }

export type Route =
  | 'home' | 'projects' | 'people' | 'utilization' | 'maintenance' | 'issues' | 'audit'

export type BootPhase = 'anon' | 'loading' | 'ready' | 'error'

const ROSTER_KEY = 'pms.callerRoster'

interface Store {
  phase: BootPhase
  bootError: string | null
  /** token = 로그인 세션 · caller = 로그인 없이 화자 지정(인증 OFF 전용) */
  sessionMode: 'token' | 'caller' | null
  me: MeView | null
  people: PersonSummary[]
  /** 개발 모드 화자 전환용 명부 — 지금까지 본 인원의 누적(가시성 축소 후에도 되돌아갈 수 있게) */
  roster: PersonSummary[]
  orgUnits: OrgUnitView[]
  /** 인력 등록 폼의 선택 목록 — 관리 권한자만 채워진다 */
  grades: GradeDetail[]
  permissionGroups: PermissionGroupDetail[]
  projects: ProjectSummary[]
  /** 서버가 알려 준 전체 건수 — 한 번에 받은 수와 다르면 화면이 안내한다 */
  totalProjects: number
  route: Route
  detail: ProjectDetail | null
  /** 열려 있는 유지보수 계약 — 프로젝트 상세와 같은 자리다(라우트 안의 상세 갈래) */
  contract: ContractDetail | null
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
  openContract: (contractId: number) => Promise<void>
  closeContract: () => void
  showToast: (message: string) => void
  createProject: (body: CreateProjectBody) => Promise<Result<ProjectDetail>>
  editProject: (body: EditProjectBody) => Promise<Result<ProjectDetail>>
  saveProgress: (progress: number, confirmed: boolean) => Promise<Result<ProgressUpdateResult>>
  complete: () => Promise<Result<ProjectDetail>>
  reopen: () => Promise<Result<ProjectDetail>>
  /**
   * 유지보수 이관 (D1-1) — 완료 상태에서만. 성공하면 프로젝트는 유지보수중이 되고
   * 그 뒤로 상태 행위가 없다(재개도 막힌다 — 이관된 계약과의 정합).
   */
  handover: (body: HandoverBody) => Promise<Result<ProjectDetail>>
  changeManager: (personId: number) => Promise<Result<ProjectDetail>>
  changeRole: (personId: number, role: ProjectRole) => Promise<Result<ProjectDetail>>
  deleteProject: () => Promise<Result<null>>
  assign: (body: CreateAssignmentBody) => Promise<Result<AssignmentView>>
  updateAssignment: (assignmentId: number, body: UpdateAssignmentBody) =>
    Promise<Result<AssignmentView>>
  closeAssignment: (assignmentId: number) => Promise<Result<null>>
  createPerson: (body: CreatePersonBody) => Promise<Result<PersonSummary>>
  /** 인력 수정 (E2-2) — 권한 그룹 부여도 이 경로다 */
  updatePerson: (personId: number, body: UpdatePersonBody) => Promise<Result<PersonSummary>>
  /**
   * 소속 이동 (E1-1) — 성공해도 경고가 실려 올 수 있다(E1-2).
   * 화면은 그 경고를 오류가 아니라 안내로 보여 준다: 이동은 실제로 일어났다.
   */
  movePersonOrgUnit: (personId: number, orgUnitId: number) => Promise<Result<OrgUnitMoveResult>>
  deactivatePerson: (personId: number) => Promise<Result<null>>
  createOrgUnit: (body: CreateOrgUnitBody) => Promise<Result<OrgUnitView>>
  renameOrgUnit: (orgUnitId: number, name: string) => Promise<Result<OrgUnitView>>
  moveOrgUnitParent: (orgUnitId: number, parentId: number) => Promise<Result<OrgUnitView>>
  deleteOrgUnit: (orgUnitId: number) => Promise<Result<null>>
  createGrade: (body: GradeBody) => Promise<Result<GradeDetail>>
  updateGrade: (gradeId: number, body: GradeBody) => Promise<Result<GradeDetail>>
  deleteGrade: (gradeId: number) => Promise<Result<null>>
  createPermissionGroup: (body: PermissionGroupBody) => Promise<Result<PermissionGroupDetail>>
  updatePermissionGroup: (groupId: number, body: PermissionGroupBody) =>
    Promise<Result<PermissionGroupDetail>>
  deletePermissionGroup: (groupId: number) => Promise<Result<null>>
  /** 유지보수 계약·사이트 쓰기 (US-D2) — 성공하면 열려 있는 계약을 다시 읽는다 */
  createContract: (body: ContractBody) => Promise<Result<ContractDetail>>
  updateContract: (contractId: number, body: ContractBody) => Promise<Result<ContractDetail>>
  addSite: (contractId: number, body: SiteBody) => Promise<Result<SiteView>>
  updateSite: (siteId: number, body: SiteBody) => Promise<Result<SiteView>>
  /**
   * 가동률 조회 — **라우트에 들어올 때 부른다**(부팅 때 미리 받지 않는다).
   * 이유: 질의에 기준 월이 있어 부팅 시점에 고를 값이 없고, 월·필터를 바꾸면
   * 매번 서버에 다시 물어야 한다(조회 시점 계산이라 캐시가 없다 — 2026-08-06).
   */
  loadUtilization: (query: UtilizationQuery) => Promise<Result<UtilizationView[]>>
  /**
   * 유지보수 조회 — 가동률과 같은 이유로 라우트 진입 시 부른다.
   * 전사 공개라 가시성 판정이 없고(D4-3) 화자가 바뀌어도 같은 답이 온다.
   */
  loadContracts: (query: ContractQuery) => Promise<Result<PageResponse<ContractSummary>>>
  /**
   * 계약 상세를 **화면 이동 없이** 읽는다 — 이슈 등록(D3-1)의 사이트 선택이 필요해서다.
   * `openContract`는 라우트를 계약 상세로 옮기므로 이슈 목록에서 쓸 수 없다.
   */
  loadContractDetail: (contractId: number) => Promise<Result<ContractDetail>>
  loadIssues: (query: IssueQuery) => Promise<Result<PageResponse<IssueView>>>
  /**
   * 이슈 쓰기 (US-D3) — 계약 쓰기와 달리 **플래그 판정이 없다**(로그인 사용자 전체).
   * 목록을 다시 읽는 것은 화면이 한다: 이슈 목록은 화면의 필터 상태가 질의라
   * store가 그 조건을 모른다(계약은 열린 상세 하나라 store가 다시 읽는다).
   */
  registerIssue: (body: IssueBody) => Promise<Result<IssueView>>
  processIssue: (issueId: number, body: IssueEditBody) => Promise<Result<IssueView>>
  addIssueComment: (issueId: number, body: CommentBody) => Promise<Result<CommentView>>
  /** 통합 감사 로그 (G1-3) — 플래그가 없으면 서버가 403이고 화면이 그대로 보여 준다 */
  loadAudit: (page: number) => Promise<Result<PageResponse<AuditRecord>>>
  /** 프로젝트별 이력 (G2-2) — 가시성 밖은 404 은닉이다 */
  loadProjectAudit: (projectId: number, page: number) =>
    Promise<Result<PageResponse<AuditRecord>>>
  // ── 알림 (EPIC F · H1-4) ──
  /**
   * 알림은 **부팅 때 한 번 읽고 그 뒤로는 SSE로 흘러든다**(F1-4, 2026-08-25).
   * 그 전에는 "부팅 + 벨 열기" 재조회였다 — 폴링을 두지 않은 것은 44명 규모에
   * 끊임없는 요청이 생기기 때문이고, 이제 스트림이 그 자리를 대신한다.
   * 재연결은 브라우저가 하고 끊겨 있던 동안의 것은 서버가 재생한다(Last-Event-ID).
   */
  notifications: NotificationView[]
  unreadNotifications: number
  loadNotifications: () => Promise<Result<PageResponse<NotificationView>>>
  markNotificationRead: (notificationId: number) => Promise<Result<null>>
  /**
   * 내 계정 (EPIC H) — 상세는 수정 폼을 되채우는 값이고, 프로필 수정은 이름·연락처를
   * 한 번에 바꾼다(서버가 두 모듈을 한 트랜잭션으로 묶는다). 비밀번호는 응답이 없다.
   *
   * 프로필이 바뀌면 `me`도 다시 읽는다 — 사이드바가 그 이름을 보여 준다.
   */
  loadMyAccount: () => Promise<Result<AccountView>>
  updateProfile: (body: UpdateProfileBody) => Promise<Result<AccountView>>
  changePassword: (body: ChangePasswordBody) => Promise<Result<null>>
  loadNotifPrefs: () => Promise<Result<NotificationPreferences>>
  updateNotifPrefs: (enabled: Record<NotificationType, boolean>) =>
    Promise<Result<NotificationPreferences>>
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
  const [people, setPeople] = useState<PersonSummary[]>([])
  const [roster, setRoster] = useState<PersonSummary[]>(restoreRoster)
  const [orgUnits, setOrgUnits] = useState<OrgUnitView[]>([])
  const [grades, setGrades] = useState<GradeDetail[]>([])
  const [permissionGroups, setPermissionGroups] = useState<PermissionGroupDetail[]>([])
  const [projects, setProjects] = useState<ProjectSummary[]>([])
  const [totalProjects, setTotalProjects] = useState(0)
  const [route, setRoute] = useState<Route>('home')
  const [detail, setDetail] = useState<ProjectDetail | null>(null)
  const [contract, setContract] = useState<ContractDetail | null>(null)
  const [toast, setToast] = useState<string | null>(null)
  const [loginError, setLoginError] = useState<string | null>(null)
  const [notifications, setNotifications] = useState<NotificationView[]>([])

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
      const [profile, visiblePeople, page, notified] = await Promise.all([
        api.me(),
        api.people(),
        api.projects(),
        api.notifications(),
      ])
      setMe(profile)
      setPeople(visiblePeople)
      setRoster((current) => mergeRoster(current, visiblePeople))
      setProjects(page.content)
      setTotalProjects(page.totalElements)
      setNotifications(notified.content)
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
    setContract(null)
    setNotifications([])
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
    // 직급·권한 그룹까지 함께 읽는다: 그룹을 지우면 인원의 그룹 이름이 바뀌고,
    // 인원을 옮기면 그룹의 memberCount가 바뀐다 — 네 목록이 서로의 표시를 정한다
    const [visiblePeople, units, gradeList, groupList] = await Promise.all([
      api.people(),
      me?.manageOrg ? api.orgUnits() : Promise.resolve([] as OrgUnitView[]),
      me?.manageOrg ? api.grades() : Promise.resolve([] as GradeDetail[]),
      me?.manageOrg
        ? api.permissionGroups()
        : Promise.resolve([] as PermissionGroupDetail[]),
    ])
    setPeople(visiblePeople)
    setOrgUnits(units)
    setGrades(gradeList)
    setPermissionGroups(groupList)
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

  /** 계약 상세 — 전사 공개라 404는 "그런 계약이 없다"는 뜻뿐이다(은닉이 아니다). */
  const openContract = useCallback(async (contractId: number) => {
    try {
      setContract(await api.maintenanceContract(contractId))
      setRoute('maintenance')
    } catch (e) {
      showToast(e instanceof Error ? e.message : '계약을 열 수 없습니다')
    }
  }, [showToast])

  const closeContract = useCallback(() => setContract(null), [])

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

  /**
   * 유지보수 쓰기 공통 (US-D2) — 성공하면 열려 있는 계약 상세를 서버 값으로 다시 읽는다.
   *
   * 응답만 화면에 반영하지 않는 이유는 계약 상세가 **합성 값**이기 때문이다: 사이트를
   * 추가하면 사이트 목록·연락처·이슈 요약이 함께 바뀌는데 `SiteView` 하나로는 그것을
   * 알 수 없다. 낙관적 락 충돌(409)일 때도 다시 읽는다 — 최신 version을 보고 재시도할
   * 수 있어야 한다(프로젝트 쪽 `run`과 같은 규칙).
   */
  const runContract = useCallback(async <T,>(
      action: () => Promise<T>, contractId?: number): Promise<Result<T>> => {
    const result = await run(action, { refresh: false })

    if (contractId !== undefined) {
      setContract(await api.maintenanceContract(contractId).catch(() => null))
    }

    return result
  }, [run])

  /*
   * 조회 동작은 **함수 정체성이 흔들리지 않게** 여기서 만든다 (2026-08-24 결함 수정).
   *
   * `value`의 useMemo 의존성에 `notifications`가 있어서, 알림을 한 번 읽으면 store 값이
   * 새로 만들어지고 그 안에서 즉석으로 만들던 이 함수들의 정체성도 함께 바뀌었다.
   * 화면들은 `useCallback(..., [loadX, ...])` + `useEffect(..., [fetchRows])`로 조회하므로
   * (규약 §3 "의존성 배열 생략 금지") 그 변화가 곧 재조회이고, 표는 `loading` 상태로
   * 되돌아갔다가 다시 그려진다 — **알림 벨을 열면 화면이 깜빡인 이유가 이것이다**.
   * 벨은 더 나빴다: 열려 있는 동안 `loadNotifications`가 알림을 갱신 → 정체성 변화 →
   * 벨의 effect 재실행 → 다시 조회로 **끝나지 않는 고리**가 됐다.
   *
   * `run`은 `[refreshProjects]`(빈 의존성)에만 매달려 있어 이미 안정적이므로, 조회
   * 동작을 `run` 하나에 묶어 두면 store 값이 몇 번 다시 만들어지든 화면의 effect는
   * 조용하다. 세터를 부르는 `loadNotifications`도 같은 이유로 여기 있다.
   */
  const loadUtilization = useCallback(
    (query: UtilizationQuery) => run(() => api.utilization(query), { refresh: false }), [run])
  const loadContracts = useCallback(
    (query: ContractQuery) => run(() => api.maintenanceContracts(query), { refresh: false }),
    [run])
  const loadIssues = useCallback(
    (query: IssueQuery) => run(() => api.maintenanceIssues(query), { refresh: false }), [run])
  // 계약 상세를 화면 이동 없이 읽는다 — 이슈 등록의 사이트 선택이 원천이다
  const loadContractDetail = useCallback(
    (contractId: number) =>
      run(() => api.maintenanceContract(contractId), { refresh: false }), [run])

  const loadAudit = useCallback(
    (page: number) => run(() => api.audit(page), { refresh: false }), [run])
  const loadProjectAudit = useCallback(
    (projectId: number, page: number) =>
      run(() => api.projectAudit(projectId, page), { refresh: false }), [run])
  const loadNotifications = useCallback(async () => {
    const result = await run(() => api.notifications(), { refresh: false })

    if (result.ok) {
      setNotifications(result.value.content)
    }

    return result
  }, [run])

  /*
   * 알림 SSE 구독 (AC F1-4, 2026-08-25) — 부팅 로드가 목록을 채우고 그 뒤로는
   * 여기로 흘러든다. 벨을 열 때마다 재조회하던 것을 이것이 대신한다.
   *
   * **의존성이 `phase`뿐인 것이 중요하다**: `notifications`를 걸면 알림이 한 건 올
   * 때마다 이 effect가 다시 돌아 연결을 끊고 다시 붙는다 — 2026-08-24에 벨이 겪은
   * "끝나지 않는 고리"와 같은 종류이고, 여기서는 그것이 재연결 폭풍이 된다.
   * 갱신은 세터의 함수형 형태로 하므로 최신 목록을 의존성으로 들 이유가 없다.
   *
   * **연결될 때마다 목록을 다시 읽는다**: 서버는 끊겨 있던 동안의 것을 재생하지
   * 않으므로(그 설계의 근거는 서버 컨트롤러 주석) 재조회가 그 구간을 메운다 —
   * AC F1-4의 "미연결이면 재연결·재조회 시 반영" 그대로다. 첫 연결에서도 도는데,
   * 부팅 로드와 겹치는 한 번의 왕복은 재연결 경로를 하나로 두는 값이다.
   *
   * 중복은 id로 거른다: 재조회가 담은 것을 스트림이 다시 보낼 수 있다.
   */
  useEffect(() => {
    if (phase !== 'ready') {
      return
    }

    return subscribeNotifications(
      (view) =>
        setNotifications((current) =>
          current.some((existing) => existing.id === view.id)
            ? current
            : [view, ...current]),
      () => void loadNotifications())
  }, [phase, loadNotifications])
  const markNotificationRead = useCallback(async (notificationId: number) => {
    const result = await run(() => api.markNotificationRead(notificationId), { refresh: false })

    if (result.ok) {
      // 서버가 본문 없는 200을 주므로 화면 상태를 직접 옮긴다 — 목록을 다시 받으면
      // 방금 읽은 줄이 사라지는 것처럼 보이고, 사용자는 무엇을 읽었는지 잃는다
      setNotifications((current) => current.map((notification) =>
        (notification.id === notificationId ? { ...notification, read: true } : notification)))
    }

    return result
  }, [run])
  const loadNotifPrefs = useCallback(
    () => run(() => api.notifPrefs(), { refresh: false }), [run])
  const updateNotifPrefs = useCallback(
    (enabled: Record<NotificationType, boolean>) =>
      run(() => api.updateNotifPrefs(enabled), { refresh: false }), [run])

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
    contract,
    toast,
    loginError,
    go: (next) => {
      setRoute(next)
      setDetail(null)
      setContract(null)
    },
    submitLogin,
    enterAsCaller,
    logout,
    reload,
    openProject,
    closeProject,
    openContract,
    closeContract,
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
    handover: (body) => run(
      () => api.handover(requireDetail(detail).id, body), { detail: requireDetail(detail).id }),
    changeManager: (personId) => run(
      () => api.changeManager(requireDetail(detail).id, personId,
        requireDetail(detail).version),
      { detail: requireDetail(detail).id }),
    changeRole: (personId, role) => run(
      () => api.changeRole(requireDetail(detail).id, personId, role),
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
    updatePerson: (personId, body) => runOrganization(() => api.updatePerson(personId, body)),
    movePersonOrgUnit: (personId, orgUnitId) =>
      runOrganization(() => api.movePersonOrgUnit(personId, { orgUnitId })),
    deactivatePerson: (personId) => runOrganization(() => api.deactivatePerson(personId)),
    createOrgUnit: (body) => runOrganization(() => api.createOrgUnit(body)),
    renameOrgUnit: (orgUnitId, name) =>
      runOrganization(() => api.renameOrgUnit(orgUnitId, { name })),
    moveOrgUnitParent: (orgUnitId, parentId) =>
      runOrganization(() => api.moveOrgUnitParent(orgUnitId, parentId)),
    deleteOrgUnit: (orgUnitId) => runOrganization(() => api.deleteOrgUnit(orgUnitId)),
    createGrade: (body) => runOrganization(() => api.createGrade(body)),
    updateGrade: (gradeId, body) => runOrganization(() => api.updateGrade(gradeId, body)),
    deleteGrade: (gradeId) => runOrganization(() => api.deleteGrade(gradeId)),
    createPermissionGroup: (body) => runOrganization(() => api.createPermissionGroup(body)),
    updatePermissionGroup: (groupId, body) =>
      runOrganization(() => api.updatePermissionGroup(groupId, body)),
    deletePermissionGroup: (groupId) =>
      runOrganization(() => api.deletePermissionGroup(groupId)),
    // 이슈 쓰기는 목록을 다시 읽지 않는다 — 화면의 필터가 질의라 여기서는 조건을
    // 모른다. 호출한 화면이 자기 조건으로 재조회한다(계약과 다른 점이다)
    registerIssue: (body) => run(() => api.registerIssue(body), { refresh: false }),
    processIssue: (issueId, body) =>
      run(() => api.processIssue(issueId, body), { refresh: false }),
    addIssueComment: (issueId, body) =>
      run(() => api.addIssueComment(issueId, body), { refresh: false }),
    createContract: (body) => runContract(() => api.createContract(body)),
    updateContract: (contractId, body) =>
      runContract(() => api.updateContract(contractId, body), contractId),
    addSite: (contractId, body) => runContract(() => api.addSite(contractId, body), contractId),
    updateSite: (siteId, body) =>
      runContract(() => api.updateSite(siteId, body), contract?.id),
    // 조회 동작은 위에서 안정화한 것을 그대로 싣는다 — 여기서 즉석으로 만들면
    // store 값이 다시 만들어질 때마다 화면의 조회 effect가 함께 깨어난다
    loadUtilization,
    loadContracts,
    loadContractDetail,
    loadIssues,
    loadAudit,
    loadProjectAudit,
    notifications,
    unreadNotifications: notifications.filter((notification) => !notification.read).length,
    loadNotifications,
    markNotificationRead,
    loadMyAccount: () => run(() => api.myAccount(), { refresh: false }),
    updateProfile: async (body) => {
      const result = await run(() => api.updateProfile(body), { refresh: false })

      // 사이드바가 이름을 보여 주므로 me를 다시 읽는다 — 안 읽으면 새로고침 전까지 옛 이름이다
      if (result.ok) {
        setMe(await api.me().catch(() => me))
      }

      return result
    },
    changePassword: (body) => run(() => api.changePassword(body), { refresh: false }),
    loadNotifPrefs,
    updateNotifPrefs,
  }), [phase, bootError, sessionMode, me, people, roster, orgUnits, grades, permissionGroups,
    projects, totalProjects, route, detail, contract, toast, loginError, submitLogin,
    enterAsCaller, logout, reload, openProject, closeProject, openContract, closeContract,
    showToast, run, runOrganization, runContract, notifications,
    loadUtilization, loadContracts, loadContractDetail, loadIssues, loadAudit, loadProjectAudit,
    loadNotifications, markNotificationRead, loadNotifPrefs, updateNotifPrefs])

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
function restoreRoster(): PersonSummary[] {
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(ROSTER_KEY) ?? '[]')

    return Array.isArray(stored) ? (stored as PersonSummary[]) : []
  } catch {
    return []
  }
}

function mergeRoster(current: PersonSummary[], loaded: PersonSummary[]): PersonSummary[] {
  const byId = new Map(current.map((person) => [person.id, person]))
  loaded.forEach((person) => byId.set(person.id, person))
  const merged = [...byId.values()].sort((a, b) => a.id - b.id)
  localStorage.setItem(ROSTER_KEY, JSON.stringify(merged))

  return merged
}
