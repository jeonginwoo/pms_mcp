/*
 * PMS 프론트 스토어 — pms_back REST 실연동판.
 *  - 인증: 이메일+비밀번호 로그인(/api/auth/login) → JWT 세션, 새로고침 시 토큰으로 복원
 *  - 데이터: 부트스트랩 시 서버에서 로드 (me · account · people · projects · assignments · maintenance · notifications · audit)
 *  - 쓰기: 진행률 2단계(서버 preview→commit) · 프로젝트/사용자 CRUD · 내 계정(프로필/비밀번호/알림 설정) → REST
 *  - 실시간: SSE(/api/notifications/stream) 구독 → 알림·프로젝트 자동 갱신
 *  - 가시성·권한: 서버가 최종 판정(403/404/409) — 로컬 roles 는 UI 노출 제어용
 *  - 조직 트리·직급·권한 탭: 백엔드 엔티티 없음 → 로컬 데모 (화면에 표기)
 */
import { createContext, useContext, useEffect, useRef, useState } from 'react'
import { api, login as apiLogin, getToken, clearToken, notificationStreamUrl } from './api.js'
import { ROLES, HQ_TEAM, THIS_YM } from './constants.js'

const StoreCtx = createContext(null)
export const useStore = () => useContext(StoreCtx)

const clone = (v) => JSON.parse(JSON.stringify(v))
const HQ_DIVISION = '프로텐' // 전사 직속(대표이사) — 조직도 파생에서 제외

/** 서버 orgRole → 화면 역할 키 */
const ROLE_KEY = { ADMIN: 'admin', DIVISION_HEAD: 'head', TEAM_LEAD: 'lead', MEMBER: 'member' }

export function StoreProvider({ children }) {
  const [S, set] = useState(() => ({
    boot: getToken() ? 'loading' : 'anon', bootError: null,
    loginErr: null,
    role: 'member', route: 'home',
    profileOpen: false, acctModal: null,
    data: { me: null, account: null, people: [], projects: [], assignments: [], maintenance: {}, notifications: { unread: 0, items: [] }, audit: [] },
    // 프로젝트 화면
    projStatus: '전체', projKw: '', selId: null,
    editPercent: 0, preview: null, saveMsg: null, delConfirm: null, projModal: null,
    // 설정 (조직/직급/권한은 로컬 데모)
    settingsTab: 'audit', userKw: '', userTeamSel: null, userModal: null, userDelConfirm: null, userEdit: null,
    orgTree: [], orgEdit: null, orgAdd: null, orgSeq: 100, companyName: '프로텐', companyEdit: null,
    grades: [], gradeForm: null, gradeSeq: 10,
    roles: clone(ROLES), roleForm: null, roleSeq: 1, roleOpen: null,
    // 가동률 · 유지보수 · 인력
    utilMonth: THIS_YM, utilTeam: '전체', maintId: null, maintType: '전체', personSel: null,
    // 알림 · 챗 · 토스트
    notifOpen: false, extraNotifs: [],
    chatOpen: false, chatMsgs: [], chatInput: '', chatBusy: false, chatBusyText: '', chatConfirm: null, reasonFor: null,
    toast: null,
  }))
  const toastT = useRef(null)
  const esRef = useRef(null)
  const convId = useRef(`c-${Date.now()}`)

  const setState = (patch) => set(prev => ({ ...prev, ...(typeof patch === 'function' ? patch(prev) : patch) }))

  // ══ 인증 · 부트스트랩 ══
  async function loginSubmit(email, password) {
    setState({ loginErr: null })
    try {
      await apiLogin(email, password)
      await bootstrap()
      return true
    } catch (e) {
      setState({ loginErr: e.message, boot: 'anon' })
      return false
    }
  }
  function logout() {
    esRef.current?.close()
    clearToken()
    setState({
      boot: 'anon', loginErr: null, route: 'home', selId: null, personSel: null,
      preview: null, saveMsg: null, chatConfirm: null, chatMsgs: [], chatOpen: false,
      notifOpen: false, profileOpen: false, acctModal: null, extraNotifs: [],
      data: { me: null, account: null, people: [], projects: [], assignments: [], maintenance: {}, notifications: { unread: 0, items: [] }, audit: [] },
    })
  }
  async function bootstrap() {
    setState({ boot: 'loading', bootError: null, selId: null, personSel: null, preview: null, saveMsg: null, chatConfirm: null, chatMsgs: [], extraNotifs: [], profileOpen: false, acctModal: null })
    try {
      const me = await api.me()
      const [account, people, projects, assignments, notifications] = await Promise.all([
        api.account(), api.people(), api.projects(), api.assignments(), api.notifications(),
      ])
      const audit = me.orgRole === 'ADMIN' ? await api.audit() : []
      // 유지보수 이력: 유지보수중 계약별 로드
      const maintProjects = projects.filter(p => p.status === '유지보수중')
      const maintEntries = await Promise.all(maintProjects.map(async p => [p.id, mapLogs(await api.maintenance(p.id))]))
      const maintenance = Object.fromEntries(maintEntries)
      // 조직 트리·직급: 서버 인원에서 파생 (로컬 데모의 초기값)
      const orgTree = deriveOrgTree(people)
      const grades = deriveGrades(people)
      setState(s => ({
        boot: 'ready',
        role: ROLE_KEY[me.orgRole] || 'member',
        data: { me, account, people, projects, assignments, maintenance, notifications, audit },
        orgTree, grades,
        maintId: s.maintId && maintProjects.some(p => p.id === s.maintId) ? s.maintId : (maintProjects[0]?.id ?? null),
      }))
      connectSse()
    } catch (e) {
      // 토큰 만료/무효(401) → 로그인 화면으로
      if (e.status === 401) { clearToken(); setState({ boot: 'anon', loginErr: null }) }
      else setState({ boot: 'error', bootError: e.message || '백엔드(8080)에 연결할 수 없습니다' })
    }
  }
  useEffect(() => { if (getToken()) bootstrap(); return () => esRef.current?.close() }, []) // eslint-disable-line

  const mapLogs = (logs) => logs.map(l => ({ ...l, at: l.occurredAt }))
  function deriveOrgTree(people) {
    const divs = [...new Set(people.map(p => p.division).filter(d => d && d !== HQ_DIVISION))]
    let id = 1
    return divs.map(d => ({
      id: id++, name: d,
      children: [...new Set(people.filter(p => p.division === d && p.team !== d).map(p => p.team))]
        .map(t => ({ id: 10 + id++, name: t, children: [] })),
    }))
  }
  function deriveGrades(people) {
    const map = new Map()
    people.forEach(p => { if (!map.has(p.grade)) map.set(p.grade, p.gradeCoeff) })
    return [...map.entries()].sort((a, b) => b[1] - a[1]).map(([name, factor], i) => ({ id: i + 1, name, factor }))
  }

  const refreshProjects = async () => {
    const [projects, assignments] = await Promise.all([api.projects(), api.assignments()])
    setState(s => ({ data: { ...s.data, projects, assignments } }))
    return projects
  }
  const refreshPeople = async () => { const people = await api.people(); setState(s => ({ data: { ...s.data, people } })) }
  const refreshNotifs = async () => { const notifications = await api.notifications(); setState(s => ({ data: { ...s.data, notifications } })) }
  const refreshAudit = () => {
    if (S.data.me?.orgRole !== 'ADMIN') return
    api.audit().then(audit => setState(x => ({ data: { ...x.data, audit } }))).catch(() => {})
  }

  function connectSse() {
    esRef.current?.close()
    const es = new EventSource(notificationStreamUrl())
    esRef.current = es
    es.addEventListener('notification', () => { refreshNotifs(); refreshProjects().catch(() => {}) })
    es.onerror = () => { /* EventSource 자동 재연결 */ }
  }

  // ══ 파생 셀렉터 ══
  const titleOf = (p) => {
    // ADMIN(대표이사)·MEMBER는 직급 표기, 부서장/팀장은 직책 표기
    const rd = S.roles.find(x => x.key === p.orgRole)
    return (!rd || rd.key === 'MEMBER' || rd.key === 'ADMIN') ? p.grade : rd.label
  }
  const people = () => S.data.people.map(p => ({ ...p, title: titleOf(p) }))
  const person = (id) => people().find(p => p.id === id) || { name: '?', grade: '', team: '', title: '', gradeCoeff: 1 }
  const projects = () => S.data.projects
  const proj = (id) => projects().find(p => p.id === id) || null
  const role = () => S.role
  const user = () => {
    const me = S.data.me
    if (!me) return { id: 0, name: '', team: '', title: '' }
    const p = people().find(x => x.id === me.personId)
    return p || { id: me.personId, name: me.name, team: me.team, grade: '', title: '', orgRole: me.orgRole }
  }
  const rolePerms = () => (S.roles.find(x => x.key === (S.data.me?.orgRole)) || {}).perms || {}
  const hasPerm = (k) => !!rolePerms()[k]

  const visibleProjects = () => projects() // 서버가 이미 가시성 선필터 적용
  const scopePeople = () => {
    const r = role(), u = user()
    let ppl = people().filter(p => p.team !== HQ_TEAM)
    if (r === 'member') ppl = ppl.filter(p => p.id === u.id)
    if ((r === 'head' || r === 'admin') && S.utilTeam !== '전체') ppl = ppl.filter(p => p.team === S.utilTeam)
    return ppl
  }
  const utilOf = (personId, month) => {
    const as = S.data.assignments.filter(a => a.personId === personId && a.month === month)
    const mm = as.reduce((s, a) => s + a.mm, 0)
    const rate = Math.round(mm * 100)
    const adj = Math.round(rate * (person(personId).gradeCoeff || 1))
    return { mm: Math.round(mm * 10) / 10, rate, adj, causes: as.map(a => ({ pname: (proj(a.projectId) || {}).name || '', mm: a.mm })) }
  }
  const canEdit = (p) => {
    const u = user(), r = role()
    if (!hasPerm('editProgress')) return false
    if (r === 'admin') return true
    if (p.pmId === u.id) return true
    return S.data.assignments.some(a => a.personId === u.id && a.projectId === p.id)
  }
  const canManage = (p) => {
    const u = user()
    if (p.pmId === u.id) return true
    if (!hasPerm('manageProj')) return false
    if (hasPerm('viewAll')) return true
    return p.team === u.team
  }
  const teams = () => { const out = []; const walk = (ns) => ns.forEach(n => { out.push(n.name); walk(n.children || []) }); walk(S.orgTree); return out }

  // ══ 공통 액션 ══
  const go = (route) => setState({ route, selId: null, notifOpen: false, preview: null, saveMsg: null })
  const openProject = (id) => {
    const p = proj(id)
    setState({ route: 'projects', selId: id, editPercent: p ? p.progress : 0, preview: null, saveMsg: null, notifOpen: false })
  }
  const notifList = () => [...S.extraNotifs, ...S.data.notifications.items]
  const pushNotif = (msg) =>
    setState(s => ({ extraNotifs: [{ id: `x${Date.now()}`, msg, at: '방금 전', unread: true, local: true }, ...s.extraNotifs] }))
  const showToast = (text) => {
    setState({ toast: text })
    clearTimeout(toastT.current)
    toastT.current = setTimeout(() => setState({ toast: null }), 3200)
  }
  const markAllNotifs = async () => {
    const unreadIds = S.data.notifications.items.filter(n => n.unread).map(n => n.id)
    setState({ extraNotifs: [], notifOpen: false })
    for (const id of unreadIds) await api.markNotificationRead(id).catch(() => {})
    refreshNotifs()
  }

  // ══ 진행률 2단계 (서버 preview → commit) ══
  const previewProgress = async (pid, percent) => {
    try { const r = await api.updateProgress(pid, { percent, version: proj(pid)?.version ?? 0, confirmed: false }); return { ok: true, summary: r.summary } }
    catch (e) { return { ok: false, error: e.message } }
  }
  const commitProgress = async (pid, percent) => {
    const p = proj(pid)
    try {
      const r = await api.updateProgress(pid, { percent, version: p?.version ?? 0, confirmed: true })
      await refreshProjects(); refreshNotifs(); refreshAudit()
      showToast(`진행률이 저장되었습니다 · ${p?.name}`)
      return { ok: true, summary: r.summary }
    } catch (e) {
      if (e.status === 409) refreshProjects() // 최신 버전 재조회
      return { ok: false, error: e.message }
    }
  }

  // ══ 프로젝트 CRUD ══
  const setForm = (k, v) =>
    setState(s => ({ projModal: { ...s.projModal, form: { ...s.projModal.form, [k]: k === 'pmId' ? Number(v) : v }, err: null } }))
  const openCreate = () => {
    const u = user()
    const leaf = teams().filter(n => !S.orgTree.some(d => d.name === n))
    const defTeam = u.team && u.team !== HQ_TEAM ? u.team : (leaf[0] || '')
    const ym = THIS_YM
    setState({ projModal: { mode: 'create', id: null, err: null, form: {
      name: '', client: '', status: '계약대기', team: defTeam, pmId: u.id,
      start: `${ym}-01`, end: `${ym.slice(0, 4)}-12-31`,
    } } })
  }
  const openEdit = (id) => {
    const p = proj(id)
    if (!p) return
    setState({ projModal: { mode: 'edit', id, err: null, form: { name: p.name, client: p.client, status: p.status, team: p.team, pmId: p.pmId, start: p.start, end: p.end } } })
  }
  const saveProjModal = async () => {
    const m = S.projModal
    if (!m) return
    const f = m.form
    const fail = (msg) => setState(s => ({ projModal: { ...s.projModal, err: msg } }))
    if (!f.name.trim()) return fail('프로젝트명을 입력해 주세요.')
    if (!f.client.trim()) return fail('고객사를 입력해 주세요.')
    if (f.start && f.end && f.end < f.start) return fail('종료일이 시작일보다 빠릅니다.')
    const body = { name: f.name.trim(), client: f.client.trim(), status: f.status, team: f.team, pmId: f.pmId, start: f.start, end: f.end }
    try {
      if (m.mode === 'create') {
        const np = await api.createProject(body)
        await refreshProjects(); refreshAudit()
        setState({ projModal: null, route: 'projects', selId: np.id, editPercent: 0, preview: null, saveMsg: null })
        pushNotif(`${user().name}님이 [${np.name}] 프로젝트를 생성했습니다.`)
        showToast(`프로젝트가 생성되었습니다 · ${np.name}`)
      } else {
        const up = await api.updateProject(m.id, body)
        await refreshProjects(); refreshAudit()
        setState({ projModal: null })
        pushNotif(`${user().name}님이 [${up.name}] 프로젝트 정보를 수정했습니다.`)
        showToast(`프로젝트 정보가 저장되었습니다 · ${up.name}`)
      }
    } catch (e) { fail(e.message) }
  }
  const deleteProj = async (id) => {
    const p = proj(id)
    if (!p) return
    try {
      await api.deleteProject(id)
      await refreshProjects(); refreshAudit()
      setState({ delConfirm: null, selId: null })
      pushNotif(`${user().name}님이 [${p.name}] 프로젝트를 삭제했습니다.`)
      showToast(`프로젝트가 삭제되었습니다 · ${p.name}`)
    } catch (e) { setState({ delConfirm: null }); showToast(e.message) }
  }

  // ══ 인원 배정 (프로젝트 상세 — 관리 권한자, 서버 반영) ══
  const upsertAssign = async (projectId, personId, month, mm) => {
    const p = person(personId)
    try {
      await api.upsertAssignment(projectId, { personId, month, mm })
      await refreshProjects(); refreshAudit()
      showToast(`배정되었습니다 · ${p.name} ${mm} M/M`)
      pushNotif(`${user().name}님이 [${(proj(projectId) || {}).name}]에 ${p.name}님을 배정했습니다 (${mm} M/M).`)
      return true
    } catch (e) { showToast(e.message); return false }
  }
  const updateAssignMm = async (projectId, personId, month, mm) => {
    try {
      await api.upsertAssignment(projectId, { personId, month, mm })
      await refreshProjects(); refreshAudit()
      showToast(`배정이 변경되었습니다 · ${person(personId).name} ${mm} M/M`)
      return true
    } catch (e) { showToast(e.message); await refreshProjects(); return false }
  }
  const removeAssign = async (projectId, personId, month) => {
    const p = person(personId)
    try {
      await api.removeAssignment(projectId, personId, month)
      await refreshProjects(); refreshAudit()
      showToast(`배정이 해제되었습니다 · ${p.name}`)
      pushNotif(`${user().name}님이 [${(proj(projectId) || {}).name}]에서 ${p.name}님 배정을 해제했습니다.`)
    } catch (e) { showToast(e.message) }
  }

  // ══ 사용자 CRUD (서버 — 관리자 전용) ══
  const coeffOf = (gradeName) => (S.grades.find(g => g.name === gradeName) || {}).factor ?? 1
  const personBody = (f) => ({ name: f.name.trim(), team: f.team, grade: f.grade, gradeCoeff: coeffOf(f.grade), orgRole: f.orgRole })
  const userEditCommit = async () => {
    const e = S.userEdit
    if (!e) return
    if (!(e.name || '').trim()) { showToast('이름을 입력해 주세요'); return }
    try {
      await api.updatePerson(e.id, personBody(e))
      await refreshPeople(); refreshAudit()
      setState({ userEdit: null })
      showToast(`사용자 정보가 저장되었습니다 · ${e.name.trim()}`)
    } catch (err) { showToast(err.message) }
  }
  const setUForm = (k, v) => setState(s => ({ userModal: { ...s.userModal, form: { ...s.userModal.form, [k]: v }, err: null } }))
  const openUserCreate = () => {
    const leaf = teams().filter(n => !S.orgTree.some(d => d.name === n))
    setState({ userModal: { mode: 'create', id: null, err: null, form: { name: '', team: leaf[0] || '', grade: S.grades[S.grades.length - 1]?.name || '', orgRole: 'MEMBER' } } })
  }
  const openUserEdit = (id) => {
    const p = people().find(x => x.id === id)
    if (!p) return
    setState({ userModal: { mode: 'edit', id, err: null, form: { name: p.name, team: p.team, grade: p.grade, orgRole: p.orgRole } } })
  }
  const saveUserModal = async () => {
    const m = S.userModal
    if (!m) return
    if (!m.form.name.trim()) { setState(s => ({ userModal: { ...s.userModal, err: '이름을 입력해 주세요.' } })); return }
    try {
      if (m.mode === 'create') {
        const np = await api.createPerson(personBody(m.form))
        await refreshPeople(); refreshAudit()
        setState({ userModal: null })
        showToast(`사용자가 추가되었습니다 · ${np.name}`)
      } else {
        await api.updatePerson(m.id, personBody(m.form))
        await refreshPeople(); refreshAudit()
        setState({ userModal: null })
        showToast(`사용자 정보가 저장되었습니다 · ${m.form.name.trim()}`)
      }
    } catch (e) { setState(s => ({ userModal: { ...s.userModal, err: e.message } })) }
  }
  const deleteUser = async (id) => {
    const p = people().find(x => x.id === id)
    try {
      await api.deletePerson(id)
      await refreshPeople(); refreshAudit()
      setState(s => ({ userDelConfirm: null, personSel: s.personSel === id ? null : s.personSel }))
      showToast(`사용자가 삭제되었습니다 · ${p ? p.name : id}`)
    } catch (e) { setState({ userDelConfirm: null }); showToast(e.message) }
  }

  // ══ 조직 · 직급 · 권한 (로컬 데모 — 백엔드 엔티티 없음) ══
  const orgFind = (id) => { let res = null; const walk = (ns) => ns.forEach(n => { if (n.id === id) res = n; walk(n.children || []) }); walk(S.orgTree); return res }
  const orgTreeMap = (ns, fn) => ns.map(n => { const m = fn(n) || n; return { ...m, children: orgTreeMap(m.children || [], fn) } })
  const orgCommit = () => {
    const act = S.orgAdd || S.orgEdit
    if (!act) return
    const val = (act.val || '').trim()
    if (!val) { showToast('이름을 입력해 주세요'); return }
    const names = teams()
    if (S.orgAdd) {
      if (names.includes(val)) { showToast(`이미 존재하는 이름입니다 · ${val}`); return }
      const node = { id: S.orgSeq, name: val, children: [] }
      const tree = S.orgAdd.parentId == null
        ? [...S.orgTree, node]
        : orgTreeMap(S.orgTree, n => n.id === S.orgAdd.parentId ? { ...n, children: [...(n.children || []), node] } : n)
      setState({ orgTree: tree, orgAdd: null, orgSeq: S.orgSeq + 1 })
      showToast(`조직이 추가되었습니다 (로컬 데모) · ${val}`)
    } else {
      const e = S.orgEdit
      let oldName = null
      const tree = orgTreeMap(S.orgTree, n => { if (n.id === e.id) { oldName = n.name; return { ...n, name: val } } return n })
      if (oldName === val) { setState({ orgEdit: null }); return }
      if (names.includes(val)) { showToast(`이미 존재하는 이름입니다 · ${val}`); return }
      setState({ orgTree: tree, orgEdit: null })
      showToast(`이름이 변경되었습니다 (로컬 데모) · ${oldName} → ${val}`)
    }
  }
  const companyCommit = () => {
    const val = (S.companyEdit || '').trim()
    if (!val) { showToast('이름을 입력해 주세요'); return }
    setState({ companyName: val, companyEdit: null })
    showToast(`회사 이름이 변경되었습니다 (로컬 데모) · ${val}`)
  }
  const orgDelete = (id) => {
    const node = orgFind(id)
    if (!node) return
    if ((node.children || []).length > 0) { showToast(`하위 조직이 있어 삭제할 수 없습니다 · ${node.name}`); return }
    const used = people().some(p => p.team === node.name) || projects().some(p => p.team === node.name)
    if (used) { showToast(`소속 인원 또는 프로젝트가 있어 삭제할 수 없습니다 · ${node.name}`); return }
    const remove = (ns) => ns.filter(n => n.id !== id).map(n => ({ ...n, children: remove(n.children || []) }))
    setState({ orgTree: remove(S.orgTree) })
    showToast(`조직이 삭제되었습니다 (로컬 데모) · ${node.name}`)
  }
  const togglePerm = (key, pk) => {
    const rd = S.roles.find(x => x.key === key)
    if (!rd) return
    if (key === 'ADMIN' && pk === 'manageOrg' && rd.perms.manageOrg) { showToast('관리자의 시스템 관리 권한은 끌 수 없습니다'); return }
    const next = !rd.perms[pk]
    setState(st => ({ roles: st.roles.map(x => x.key === key ? { ...x, perms: { ...x.perms, [pk]: next } } : x) }))
    showToast(`[${rd.label}] ` + (next ? '권한이 켜졌습니다 (로컬 데모)' : '권한이 꺼졌습니다 (로컬 데모)'))
  }
  const roleCommit = () => {
    const rf = S.roleForm
    if (!rf) return
    const label = (rf.label || '').trim()
    if (!label) { showToast('권한 이름을 입력해 주세요'); return }
    if (rf.mode === 'create') {
      if (S.roles.some(r => r.label === label)) { showToast(`이미 존재하는 권한입니다 · ${label}`); return }
      const key = `ROLE_${S.roleSeq}`
      setState(st => ({
        roles: [...st.roles, { key, label, desc: (rf.desc || '').trim(), fixed: false, perms: { viewAll: false, createProj: false, manageProj: false, editProgress: false, manageOrg: false } }],
        roleForm: null, roleSeq: st.roleSeq + 1, roleOpen: key,
      }))
      showToast(`권한이 추가되었습니다 (로컬 데모) · ${label}`)
    } else {
      setState(st => ({ roles: st.roles.map(r => r.key === rf.key ? { ...r, label, desc: (rf.desc || '').trim() } : r), roleForm: null }))
      showToast(`권한이 수정되었습니다 (로컬 데모) · ${label}`)
    }
  }
  const roleDelete = (key) => {
    const rd = S.roles.find(r => r.key === key)
    if (!rd || rd.fixed) return
    if (people().some(p => p.orgRole === key)) { showToast(`인원이 있는 권한은 삭제할 수 없습니다 · ${rd.label}`); return }
    setState(st => ({ roles: st.roles.filter(r => r.key !== key), roleOpen: st.roleOpen === key ? null : st.roleOpen }))
    showToast(`권한이 삭제되었습니다 (로컬 데모) · ${rd.label}`)
  }
  const gradeCommit = () => {
    const gf = S.gradeForm
    if (!gf) return
    const name = (gf.name || '').trim()
    const factor = Number(gf.factor)
    if (!name) { showToast('직급명을 입력해 주세요'); return }
    if (!(factor > 0 && factor <= 5)) { showToast('보정계수는 0보다 크고 5 이하여야 합니다'); return }
    if (gf.mode === 'create') {
      if (S.grades.some(g => g.name === name)) { showToast(`이미 존재하는 직급입니다 · ${name}`); return }
      setState(st => ({ grades: [...st.grades, { id: st.gradeSeq, name, factor }], gradeForm: null, gradeSeq: st.gradeSeq + 1 }))
      showToast(`직급이 추가되었습니다 (로컬 데모) · ${name} (×${factor})`)
    } else {
      setState(st => ({ grades: st.grades.map(g => g.id === gf.id ? { ...g, name, factor } : g), gradeForm: null }))
      showToast(`직급이 수정되었습니다 (로컬 데모) · ${name} (×${factor})`)
    }
  }
  const gradeDelete = (id) => {
    const g = S.grades.find(x => x.id === id)
    if (!g) return
    if (people().some(p => p.grade === g.name)) { showToast(`해당 직급 인원이 있어 삭제할 수 없습니다 · ${g.name}`); return }
    setState(st => ({ grades: st.grades.filter(x => x.id !== id) }))
    showToast(`직급이 삭제되었습니다 (로컬 데모) · ${g.name}`)
  }

  // ══ 내 계정 (프로필 · 비밀번호 · 알림 설정) ══
  const notifPrefs = () => {
    const def = { progress: true, project: true, org: false, weekly: false }
    try { return { ...def, ...(JSON.parse(S.data.account?.notifPrefs || '{}')) } } catch { return def }
  }
  const openAcct = (tab) => {
    const acc = S.data.account || {}
    setState({
      profileOpen: false,
      acctModal: { tab, err: null, form: { name: acc.name || '', email: acc.email || '', phone: acc.phone || '', pwCur: '', pwNew: '', pwNew2: '' } },
    })
  }
  const setAcct = (k, v) => setState(s => ({ acctModal: { ...s.acctModal, err: null, form: { ...s.acctModal.form, [k]: v } } }))
  const acctErr = (msg) => setState(s => ({ acctModal: { ...s.acctModal, err: msg } }))
  const refreshAccount = async () => {
    const account = await api.account()
    setState(s => ({ data: { ...s.data, account } }))
  }
  const saveAcct = async () => {
    const m = S.acctModal
    if (!m) return
    const f = m.form
    if (m.tab === 'profile') {
      if (!f.name.trim()) return acctErr('이름을 입력해 주세요.')
      if (!f.email.trim() || !f.email.includes('@')) return acctErr('올바른 이메일을 입력해 주세요.')
      try {
        await api.updateProfile({ name: f.name.trim(), email: f.email.trim(), phone: f.phone.trim() })
        await refreshAccount(); refreshPeople(); refreshAudit()
        setState({ acctModal: null })
        showToast('프로필이 저장되었습니다')
      } catch (e) { acctErr(e.message) }
    } else if (m.tab === 'password') {
      if (f.pwNew.length < 8) return acctErr('새 비밀번호는 8자 이상이어야 합니다.')
      if (f.pwNew !== f.pwNew2) return acctErr('새 비밀번호가 서로 일치하지 않습니다.')
      try {
        await api.changePassword(f.pwCur, f.pwNew)
        setState({ acctModal: null })
        showToast('비밀번호가 변경되었습니다')
      } catch (e) { acctErr(e.message) } // 현재 비밀번호 불일치 등 서버 판정 그대로
    }
  }
  const toggleNotifPref = async (k) => {
    const next = { ...notifPrefs(), [k]: !notifPrefs()[k] }
    try {
      const account = await api.updateNotifPrefs(next)
      setState(s => ({ data: { ...s.data, account } }))
    } catch (e) { showToast(e.message) }
  }

  // ══ AI 챗 — BFF(/api/chat) 실호출 (ai-host 미기동 시 서버가 폴백 문구 반환) ══
  const busyLabel = (t) => {
    if (/오버부킹|과부하/.test(t)) return '오버부킹 조회 중 (list_overbooked)…'
    if (/가동률|여유/.test(t)) return '가동률 조회 중 (get_utilization)…'
    if (/진행률|진척/.test(t)) return '변경 요약 생성 중 (update_progress)…'
    if (/유지보수|장애/.test(t)) return '이력 조회 중 (list_maintenance_logs)…'
    return '프로젝트 조회 중 (search_projects)…'
  }
  const sendChat = async (text) => {
    const t = String(text ?? S.chatInput).trim()
    if (!t || S.chatBusy) return
    setState(s => ({ chatMsgs: [...s.chatMsgs, { role: 'user', text: t }], chatInput: '', chatBusy: true, chatBusyText: busyLabel(t), reasonFor: null }))
    try {
      const res = await api.chat(convId.current, t)
      setState(s => ({ chatMsgs: [...s.chatMsgs, { role: 'ai', text: res.answer, fb: null }], chatBusy: false }))
      // AI가 쓰기를 수행했을 수 있으므로 최신화
      refreshProjects().catch(() => {}); refreshNotifs(); refreshAudit()
    } catch (e) {
      setState(s => ({ chatMsgs: [...s.chatMsgs, { role: 'ai', text: `오류: ${e.message}`, fb: null }], chatBusy: false }))
    }
  }
  const sendFeedback = (rating, reason) => api.chatFeedback(convId.current, rating, reason).catch(() => {})
  const runConfirm = () => {} // 챗 확인 카드는 ai-host 구조화 응답 연동 시 활성화 (FR-AI-04 후속)

  // ══ 표시 유틸 ══
  const rateColor = (adj) => adj > 100 ? 'var(--danger)' : adj >= 80 ? 'var(--ok)' : adj >= 40 ? 'var(--primary)' : 'var(--muted2)'
  const stColors = (status) => ({
    '진행중': ['#2f6fed', 'rgba(47,111,237,.12)'],
    '완료': ['#1f9d57', 'rgba(31,157,87,.13)'],
    '유지보수중': ['#b9820f', 'rgba(185,130,15,.13)'],
    '수주확정': ['#6b5bd2', 'rgba(107,91,210,.12)'],
    '계약대기': ['#8b93a3', 'rgba(139,147,163,.15)'],
  })[status] || ['#8b93a3', 'rgba(139,147,163,.15)']
  const dday = (end) => {
    const days = Math.round((new Date(end) - new Date()) / 86400000)
    if (days < 0) return { text: '종료', color: 'var(--muted2)' }
    if (days <= 30) return { text: `D-${days}`, color: 'var(--danger)' }
    if (days <= 90) return { text: `D-${days}`, color: 'var(--warn)' }
    return { text: `D-${days}`, color: 'var(--muted2)' }
  }
  const fmtPeriod = (p) => { const f = (d) => (d || '').slice(2).replace(/-/g, '.'); return `${f(p.start)} ~ ${f(p.end)}` }
  const mkLog = (l) => {
    const map = { '장애': ['장애', 'rgba(216,58,58,.12)', '#d83a3a'], 'SR': ['SR', 'rgba(47,111,237,.12)', '#2f6fed'], '정기점검': ['점검', 'rgba(139,147,163,.15)', '#8b93a3'], '패치': ['패치', 'rgba(185,130,15,.13)', '#b9820f'] }
    const [short, bg, c] = map[l.type] || ['•', 'rgba(139,147,163,.15)', '#8b93a3']
    return { ...l, typeShort: short, iconBg: bg, iconColor: c }
  }

  const value = {
    S, setState, bootstrap, loginSubmit, logout,
    notifPrefs, openAcct, setAcct, saveAcct, toggleNotifPref,
    people, person, projects, proj, role, user, hasPerm,
    visibleProjects, scopePeople, utilOf, canEdit, canManage, teams,
    go, openProject, notifList, pushNotif, showToast, markAllNotifs,
    auditList: () => S.data.audit,
    previewProgress, commitProgress,
    setForm, openCreate, openEdit, saveProjModal, deleteProj,
    upsertAssign, updateAssignMm, removeAssign,
    orgCommit, companyCommit, orgDelete, orgFind,
    togglePerm, roleCommit, roleDelete, gradeCommit, gradeDelete,
    userEditCommit, setUForm, openUserCreate, openUserEdit, saveUserModal, deleteUser,
    sendChat, sendFeedback, runConfirm,
    rateColor, stColors, dday, fmtPeriod, mkLog,
    ASSIGNMENTS: S.data.assignments, MAINT: S.data.maintenance,
  }

  return <StoreCtx.Provider value={value}>{children}</StoreCtx.Provider>
}
