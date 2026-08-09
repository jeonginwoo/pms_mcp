// 설정 — 관리 권한(권한 그룹의 manageOrg) 전용: 사용자 관리(US-E2) · 조직 구조(트리) · 직급 · 권한 그룹 · 통합 감사로그(G1-3)
import { useState } from 'react'
import {
  addOrgUnit, deactivatePerson, deleteGrade, deleteOrgUnit, deleteRoleGroup,
  renameOrgUnit, saveGrade, savePerson, saveRoleGroup, useApp,
} from '../core/store'
import { groupOf, orgIsAdmin, SCOPE_LABEL } from '../core/permissions'
import { Empty, ErrorBox, Field, Modal } from '../components/ui'
import type { Grade, OrgUnit, Person, RoleGroup } from '../types'

export default function Settings() {
  const s = useApp()
  const me = s.people.find((p) => p.id === s.currentUserId)!
  const [tab, setTab] = useState<'사용자 관리' | '조직 관리' | '감사 로그'>('사용자 관리')

  if (!orgIsAdmin(me, s.roleGroups)) {
    return <ErrorBox>설정은 관리 권한 전용입니다. (서버도 403으로 거절합니다 — G1-3·E2-4)</ErrorBox>
  }
  return (
    <>
      <h1 className="page">설정</h1>
      <p className="page-desc">사용자 관리(US-E2) · 조직 구조·직급·권한(조직 관리) · 통합 감사로그(G1-3 — 조직·계정 변경까지 전체).</p>
      <div className="tabs">
        {(['사용자 관리', '조직 관리', '감사 로그'] as const).map((t) => (
          <button key={t} className={tab === t ? 'active' : ''} onClick={() => setTab(t)}>{t}</button>
        ))}
      </div>
      {tab === '사용자 관리' && <Users />}
      {tab === '조직 관리' && <OrgTab />}
      {tab === '감사 로그' && <AuditLog />}
    </>
  )
}

// ── 조직 관리 — 조직 구조(좌) + 직급·권한 관리(우) 한 탭 구성 (구 화면 시안) ──
function OrgTab() {
  const s = useApp()
  const [msg, setMsg] = useState('')
  return (
    <>
      {msg && <ErrorBox>{msg}</ErrorBox>}
      <div className="org-layout">
        <div className="card">
          <h3>조직 구조</h3>
          <p style={{ color: 'var(--muted)', fontSize: 12.5, marginTop: -6 }}>
            조직은 원하는 깊이까지 추가할 수 있습니다. 이름을 바꾸면 소속 인원·프로젝트에 함께 반영되고,
            인원·프로젝트·하위 조직이 있으면 삭제할 수 없습니다.
          </p>
          <OrgNode unit={s.orgUnits.find((u) => u.parentId === null)!} depth={0} onError={setMsg} />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <GradesCard onError={setMsg} />
          <RoleGroupsCard onError={setMsg} />
        </div>
      </div>
    </>
  )
}

// ── 사용자 관리 ─────────────────────────────────────────
function Users() {
  const s = useApp()
  const [edit, setEdit] = useState<Person | 'new' | null>(null)
  const [msg, setMsg] = useState('')
  const rows = s.people.filter((p) => p.active)
  return (
    <div className="card">
      <div className="toolbar">
        <span style={{ flex: 1, fontSize: 13, color: 'var(--muted)' }}>
          {rows.length}명 · 신규 계정은 email + 초기 비밀번호 proten1! 규칙으로 생성됩니다(부록 B) · 권한 그룹은 여기서 사용자별로 부여합니다(관리자 포함)
        </span>
        <button className="btn primary sm" onClick={() => setEdit('new')}>+ 사용자 등록</button>
      </div>
      <table>
        <thead><tr><th>이름</th><th>이메일</th><th>소속</th><th>직급 (계수)</th><th>권한 그룹</th><th>billable</th><th></th></tr></thead>
        <tbody>
          {rows.map((p) => (
            <tr key={p.id}>
              <td><b>{p.name}</b>{p.isSystem && <span className="badge purple" style={{ marginLeft: 6 }}>시스템 · 삭제 불가</span>}</td>
              <td style={{ fontSize: 12 }}>{p.email}</td>
              <td style={{ color: 'var(--muted)' }}>{p.division} · {p.team}</td>
              <td>{p.isSystem ? '—' : `${p.grade} (${p.gradeCoeff})`}</td>
              <td><span className="badge gray">{groupOf(p, s.roleGroups).name}</span></td>
              <td>{p.billable ? 'O' : '✕'}</td>
              <td style={{ display: 'flex', gap: 4 }}>
                {!p.isSystem && (
                  <>
                    <button className="btn sm" onClick={() => setEdit(p)}>수정</button>
                    <button
                      className="btn sm danger"
                      onClick={() => {
                        if (confirm(`${p.name}님을 비활성화할까요? (soft — 과거 배정·감사·집계 보존)`)) {
                          const r = deactivatePerson(p.id)
                          setMsg(r.ok ? '' : (r as any).message)
                        }
                      }}
                    >비활성</button>
                  </>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {msg && <ErrorBox>{msg}</ErrorBox>}
      {edit && <UserModal person={edit === 'new' ? undefined : edit} onClose={() => setEdit(null)} />}
    </div>
  )
}

function UserModal({ person, onClose }: { person?: Person; onClose: () => void }) {
  const s = useApp()
  const units = s.orgUnits // 회사(root) 직속 배치 허용 — 대표 등
  const grades = s.grades.map((g) => g.name)
  const [form, setForm] = useState({
    name: person?.name ?? '', team: person?.team ?? units[0]?.name ?? '',
    grade: person?.grade ?? '선임', orgRole: person?.orgRole ?? 'MEMBER',
  })
  const [msg, setMsg] = useState('')
  const submit = () => {
    const r = savePerson({ ...form, id: person?.id })
    if (!r.ok) { setMsg(`${r.code}: ${r.message}`); return }
    onClose()
  }
  return (
    <Modal title={person ? '사용자 수정' : '사용자 등록'} onClose={onClose}>
      <Field label="이름 *"><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
      <Field label="소속 조직">
        <select value={form.team} onChange={(e) => setForm({ ...form, team: e.target.value })}>
          {units.map((u) => <option key={u.id} value={u.name}>{u.name}</option>)}
        </select>
      </Field>
      <Field label="직급">
        <select value={form.grade} onChange={(e) => setForm({ ...form, grade: e.target.value })}>
          {grades.map((g) => <option key={g} value={g}>{g}</option>)}
        </select>
      </Field>
      <Field label="권한 그룹 — 관리자 권한도 여기서 부여">
        <select value={form.orgRole} onChange={(e) => setForm({ ...form, orgRole: e.target.value })}>
          {s.roleGroups.map((g) => <option key={g.key} value={g.key}>{g.name} — {SCOPE_LABEL[g.scope]}</option>)}
        </select>
      </Field>
      {msg && <ErrorBox>{msg}</ErrorBox>}
      <div className="actions">
        <button className="btn" onClick={onClose}>취소</button>
        <button className="btn primary" onClick={submit}>저장</button>
      </div>
    </Modal>
  )
}

// ── 직급 관리 카드 ──────────────────────────────────────
function GradesCard({ onError }: { onError: (m: string) => void }) {
  const s = useApp()
  const [editGrade, setEditGrade] = useState<Grade | 'new' | null>(null)
  const run = (r: { ok: boolean }) => onError(r.ok ? '' : `${(r as any).code}: ${(r as any).message}`)
  const gradeCount = (grade: string) => s.people.filter((p) => p.active && !p.isSystem && p.grade === grade).length
  return (
    <div className="card">
      <div className="toolbar" style={{ marginBottom: 4 }}>
        <h3 style={{ margin: 0, flex: 1 }}>직급 관리</h3>
        <button className="btn primary sm" onClick={() => setEditGrade('new')}>+ 직급</button>
      </div>
      <p style={{ color: 'var(--muted)', fontSize: 12.5, marginTop: 0 }}>보정계수는 가동률 계산에 사용됩니다.</p>
      <table>
        <thead><tr><th>직급</th><th style={{ textAlign: 'right' }}>보정계수</th><th style={{ textAlign: 'right' }}>인원</th><th style={{ textAlign: 'right' }}>관리</th></tr></thead>
        <tbody>
          {s.grades.map((g) => (
            <tr key={g.name}>
              <td><b>{g.name}</b></td>
              <td style={{ textAlign: 'right' }}>×{g.coeff}</td>
              <td style={{ textAlign: 'right' }}>{gradeCount(g.name)}명</td>
              <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                <button className="btn sm" onClick={() => setEditGrade(g)}>수정</button>{' '}
                <button className="btn sm danger" onClick={() => run(deleteGrade(g.name))}>삭제</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {editGrade && <GradeModal grade={editGrade === 'new' ? undefined : editGrade} onClose={() => setEditGrade(null)} />}
    </div>
  )
}

function OrgNode({ unit, depth, onError }: { unit: OrgUnit; depth: number; onError: (m: string) => void }) {
  const s = useApp()
  const children = s.orgUnits.filter((u) => u.parentId === unit.id)
  const members = s.people.filter((p) => p.active && !p.isSystem && p.team === unit.name).length
  const projects = s.projects.filter((p) => !p.deleted && p.team === unit.name).length
  const isRoot = unit.parentId === null

  const run = (r: { ok: boolean }) => onError(r.ok ? '' : `${(r as any).code}: ${(r as any).message}`)
  const add = () => {
    const name = prompt(`[${unit.name}] 아래에 만들 조직 이름:`)
    if (name) run(addOrgUnit(unit.id, name))
  }
  const rename = () => {
    const name = prompt('새 이름:', unit.name)
    if (name && name !== unit.name) run(renameOrgUnit(unit.id, name))
  }
  const remove = () => {
    if (confirm(`[${unit.name}] 조직을 삭제할까요?`)) run(deleteOrgUnit(unit.id))
  }

  return (
    <div style={{ marginLeft: depth === 0 ? 0 : 18 }}>
      <div className="org-node">
        <span className="org-icon">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            {isRoot ? <path d="M3 21h18 M5 21V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16 M9 7h2 M13 7h2 M9 11h2 M13 11h2 M9 15h2 M13 15h2" /> : children.length > 0 ? <path d="M3 19V7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2" /> : <path d="M9 11.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7 M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6 M16 4.6a3.5 3.5 0 0 1 0 6.8 M17.5 14.4c2.1.8 3.5 2.9 3.5 5.6" />}
          </svg>
        </span>
        <b>{unit.name}</b>
        <span style={{ color: 'var(--muted)', fontSize: 12 }}>
          {isRoot ? '회사' : <>{children.length > 0 && `하위 ${children.length} · `}인원 {members} · 프로젝트 {projects}</>}
        </span>
        <span style={{ flex: 1 }} />
        <button className="btn sm" onClick={add}>+ 하위</button>
        <button className="btn sm" onClick={rename}>이름 변경</button>
        {!isRoot && <button className="btn sm danger" onClick={remove}>삭제</button>}
      </div>
      {children.map((c) => <OrgNode key={c.id} unit={c} depth={depth + 1} onError={onError} />)}
    </div>
  )
}

function GradeModal({ grade, onClose }: { grade?: Grade; onClose: () => void }) {
  const [name, setName] = useState(grade?.name ?? '')
  const [coeff, setCoeff] = useState(grade?.coeff ?? 1.0)
  const [msg, setMsg] = useState('')
  const submit = () => {
    const r = saveGrade(name.trim(), coeff, grade?.name)
    if (!r.ok) { setMsg(`${r.code}: ${r.message}`); return }
    onClose()
  }
  return (
    <Modal title={grade ? '직급 수정' : '직급 추가'} onClose={onClose}>
      <Field label="직급 이름 *"><input value={name} onChange={(e) => setName(e.target.value)} /></Field>
      <Field label="보정계수 (보정 가동률 분모에 곱함)"><input type="number" step={0.1} min={0.1} value={coeff} onChange={(e) => setCoeff(Number(e.target.value))} /></Field>
      {msg && <ErrorBox>{msg}</ErrorBox>}
      <div className="actions">
        <button className="btn" onClick={onClose}>취소</button>
        <button className="btn primary" onClick={submit}>저장</button>
      </div>
    </Modal>
  )
}

// ── 권한 관리 카드 — [권한 ▾] 펼침에서 기능을 켜고 끄면 즉시 반영 (구 화면 시안) ──
function RoleGroupsCard({ onError }: { onError: (m: string) => void }) {
  const s = useApp()
  const [edit, setEdit] = useState<RoleGroup | 'new' | null>(null)
  const [openKey, setOpenKey] = useState<string | null>(null)
  const count = (key: string) => s.people.filter((p) => p.active && p.orgRole === key).length
  const featureText = (g: RoleGroup) => {
    const f: string[] = [`${SCOPE_LABEL[g.scope]} 조회`]
    if (g.adminAll) f.push('전 프로젝트 관리')
    if (g.createProject) f.push('프로젝트 생성')
    if (g.manageContract) f.push('유지보수 계약 관리')
    if (g.manageOrg) f.push('사용자/조직/권한 관리')
    return f.join(' · ')
  }
  const patch = (g: RoleGroup, part: Partial<RoleGroup>) => {
    const r = saveRoleGroup({ key: g.key, name: g.name, scope: g.scope, createProject: g.createProject, manageContract: g.manageContract, manageOrg: g.manageOrg, adminAll: g.adminAll, ...part })
    onError(r.ok ? '' : `${(r as any).code}: ${(r as any).message}`)
  }
  return (
    <div className="card">
      <div className="toolbar" style={{ marginBottom: 4 }}>
        <h3 style={{ margin: 0, flex: 1 }}>권한 관리</h3>
        <button className="btn primary sm" onClick={() => setEdit('new')}>+ 권한</button>
      </div>
      <p style={{ color: 'var(--muted)', fontSize: 12.5, marginTop: 0 }}>
        권한 ▾에서 권한별 기능을 켜고 끌 수 있으며 즉시 반영됩니다. 인원이 있는 권한은 삭제할 수 없습니다.
        사용자에게 권한 부여는 [사용자 관리] 탭에서 합니다. 관리자는 시스템 고정입니다(자기 잠금 방지).
      </p>
      {s.roleGroups.map((g) => (
        <div key={g.key}>
          <div className="perm-row">
            <span className="badge purple">{g.name}</span>
            <span style={{ fontSize: 12.5, color: 'var(--muted)' }}>{featureText(g)}</span>
            <span style={{ flex: 1 }} />
            <span style={{ fontSize: 12.5, whiteSpace: 'nowrap' }}>{count(g.key)}명</span>
            <button className="btn sm" disabled={g.system} onClick={() => setOpenKey(openKey === g.key ? null : g.key)}>
              권한 {openKey === g.key ? '▴' : '▾'}
            </button>
            <button className="btn sm" disabled={g.system} onClick={() => setEdit(g)}>수정</button>
            {!g.system && count(g.key) === 0 && (
              <button className="btn sm danger" onClick={() => {
                const r = deleteRoleGroup(g.key)
                onError(r.ok ? '' : `${(r as any).code}: ${(r as any).message}`)
              }}>삭제</button>
            )}
          </div>
          {openKey === g.key && !g.system && (
            <div className="perm-drop">
              <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13, marginBottom: 8 }}>
                조회 가시성
                <select value={g.scope} onChange={(e) => patch(g, { scope: e.target.value as RoleGroup['scope'] })}>
                  {(['ALL', 'DIVISION', 'TEAM', 'SELF'] as const).map((sc) => <option key={sc} value={sc}>{SCOPE_LABEL[sc]}</option>)}
                </select>
              </label>
              {([
                ['createProject', '프로젝트 생성', '프로젝트 등록 버튼·API'],
                ['manageContract', '유지보수 계약 관리', '계약·사이트 등록/수정'],
                ['adminAll', '전 프로젝트 관리', '모든 프로젝트에서 PM으로 간주(§4-1 치환)'],
                ['manageOrg', '사용자/조직/권한 관리', '설정 화면 접근 — 관리자와 동급 주의'],
              ] as const).map(([k, label, desc]) => (
                <label key={k} className="perm-flag">
                  <button
                    className={`perm-toggle ${g[k] ? 'on' : ''}`}
                    onClick={(e) => { e.preventDefault(); patch(g, { [k]: !g[k] } as Partial<RoleGroup>) }}
                  />
                  <span><b>{label}</b> <span style={{ color: 'var(--muted)' }}>— {desc}</span></span>
                </label>
              ))}
            </div>
          )}
        </div>
      ))}
      {edit && <RoleGroupModal group={edit === 'new' ? undefined : edit} onClose={() => setEdit(null)} />}
    </div>
  )
}

function RoleGroupModal({ group, onClose }: { group?: RoleGroup; onClose: () => void }) {
  const [form, setForm] = useState({
    name: group?.name ?? '', scope: group?.scope ?? ('TEAM' as RoleGroup['scope']),
    createProject: group?.createProject ?? false, manageContract: group?.manageContract ?? false,
    manageOrg: group?.manageOrg ?? false, adminAll: group?.adminAll ?? false,
  })
  const [msg, setMsg] = useState('')
  const submit = () => {
    const r = saveRoleGroup({ ...form, key: group?.key })
    if (!r.ok) { setMsg(`${r.code}: ${r.message}`); return }
    onClose()
  }
  const flag = (k: 'createProject' | 'manageContract' | 'manageOrg' | 'adminAll', label: string, desc: string) => (
    <label style={{ display: 'flex', gap: 8, alignItems: 'baseline', fontSize: 13, marginBottom: 8 }}>
      <input type="checkbox" checked={form[k]} onChange={(e) => setForm({ ...form, [k]: e.target.checked })} />
      <span><b>{label}</b> — <span style={{ color: 'var(--muted)' }}>{desc}</span></span>
    </label>
  )
  return (
    <Modal title={group ? `권한 그룹 수정 — ${group.name}` : '권한 그룹 추가'} onClose={onClose}>
      <Field label="그룹 이름 *"><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
      <Field label="조회 가시성 범위">
        <select value={form.scope} onChange={(e) => setForm({ ...form, scope: e.target.value as RoleGroup['scope'] })}>
          {(['ALL', 'DIVISION', 'TEAM', 'SELF'] as const).map((sc) => <option key={sc} value={sc}>{SCOPE_LABEL[sc]}</option>)}
        </select>
      </Field>
      <Field label="기능">
        <div>
          {flag('createProject', '프로젝트 생성', '프로젝트 등록 버튼·API')}
          {flag('manageContract', '유지보수 계약 관리', '계약·사이트 등록/수정')}
          {flag('adminAll', '전 프로젝트 관리', '모든 프로젝트에서 PM으로 간주(§4-1 치환)')}
          {flag('manageOrg', '사용자/조직/권한 관리', '설정 화면 접근 — 부여 시 관리자와 동급 주의')}
        </div>
      </Field>
      {msg && <ErrorBox>{msg}</ErrorBox>}
      <div className="actions">
        <button className="btn" onClick={onClose}>취소</button>
        <button className="btn primary" onClick={submit}>저장</button>
      </div>
    </Modal>
  )
}

// ── 통합 감사로그 ───────────────────────────────────────
function AuditLog() {
  const s = useApp()
  const [entity, setEntity] = useState('')
  const entities = [...new Set(s.audit.map((a) => a.entityType))]
  const rows = s.audit.filter((a) => !entity || a.entityType === entity)
  return (
    <div className="card">
      <div className="toolbar">
        <select value={entity} onChange={(e) => setEntity(e.target.value)}>
          <option value="">대상 전체</option>
          {entities.map((x) => <option key={x} value={x}>{x}</option>)}
        </select>
        <span style={{ fontSize: 12.5, color: 'var(--muted)' }}>append-only — 수정·삭제 API 없음 (G1-2). 프로젝트 스코프 행은 각 프로젝트의 이력 탭에도 보입니다(같은 행).</span>
      </div>
      {rows.length === 0 ? <Empty>기록이 없습니다.</Empty> : (
        <table>
          <thead><tr><th>일시</th><th>행위자</th><th>동작</th><th>대상</th><th>projectId</th><th>before</th><th>after</th><th>source</th></tr></thead>
          <tbody>
            {rows.map((a) => (
              <tr key={a.id}>
                <td style={{ whiteSpace: 'nowrap' }}>{a.at}</td>
                <td>{s.people.find((p) => p.id === a.actorId)?.name}</td>
                <td><span className="badge gray">{a.action}</span></td>
                <td>{a.entityType} #{a.entityId}</td>
                <td>{a.projectId ?? '—'}</td>
                <td style={{ fontSize: 12, color: 'var(--muted)' }}>{a.before ?? '—'}</td>
                <td style={{ fontSize: 12 }}>{a.after ?? '—'}</td>
                <td><span className={`badge ${a.source === 'MCP' ? 'blue' : 'gray'}`}>{a.source}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
