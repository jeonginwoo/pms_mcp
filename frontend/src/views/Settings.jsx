import { useStore } from '../store.jsx'
import { MCP_TOOLS } from '../constants.js'

const ROLE_COLORS = {
  DIVISION_HEAD: ['rgba(107,91,210,.12)', '#6b5bd2'],
  TEAM_LEAD: ['rgba(47,111,237,.12)', '#2f6fed'],
  ADMIN: ['rgba(185,130,15,.13)', '#b9820f'],
  MEMBER: ['rgba(139,147,163,.15)', '#8b93a3'],
}
const roleColor = (key) => ROLE_COLORS[key] || ['rgba(31,157,87,.13)', '#1f9d57']

/** 백엔드 엔티티 미연동(고정 enum) 항목 표시 */
function DemoBadge() {
  return <span className="badge" style={{ fontSize: 10, fontWeight: 700, background: 'var(--chip)', color: 'var(--muted2)', verticalAlign: 'middle', marginLeft: 6 }}>로컬 데모</span>
}

const PERM_DEFS = [
  ['viewAll', '전체 프로젝트 조회', '부서 전체 프로젝트 · 가동률 조회'],
  ['createProj', '프로젝트 생성', '새 프로젝트 등록'],
  ['manageProj', '프로젝트 관리', '정보 수정 · 삭제 (팀 범위는 소속 팀만)'],
  ['editProgress', '진행률 수정', '담당 프로젝트 진행률 변경'],
  ['manageOrg', '시스템 관리', '사용자 · 조직 · 직급 · 권한 관리'],
]

export default function Settings() {
  const { S, setState, hasPerm } = useStore()
  if (!hasPerm('manageOrg')) {
    return (
      <div className="confirm-card" style={{ fontSize: 13 }}>
        관리 기능은 시스템 관리자 권한이 필요합니다. 관리자 계정(<strong>신현랑 · mgecul13@proten.co.kr</strong>)으로 로그인해 주세요.
      </div>
    )
  }
  const tabs = [['audit', '감사 로그'], ['mcp', 'MCP 도구'], ['users', '사용자 관리'], ['org', '조직 관리']]
  return (
    <div>
      <div className="seg">
        {tabs.map(([k, label]) => (
          <button key={k} className={S.settingsTab === k ? 'on' : ''} onClick={() => setState({ settingsTab: k })}>{label}</button>
        ))}
      </div>
      {S.settingsTab === 'audit' && <AuditTab />}
      {S.settingsTab === 'mcp' && <McpTab />}
      {S.settingsTab === 'users' && <UsersTab />}
      {S.settingsTab === 'org' && <OrgTab />}
    </div>
  )
}

function AuditTab() {
  const { auditList } = useStore()
  const GRID = '150px 90px minmax(0,1fr) 64px'
  return (
    <section className="card">
      <h2 style={{ marginBottom: 4 }}>감사 로그</h2>
      <div className="muted2" style={{ fontSize: 12, marginBottom: 12 }}>모든 조회·변경은 출처(WEB / MCP)와 함께 기록됩니다.</div>
      <div className="thead" style={{ gridTemplateColumns: GRID, gap: 12, padding: '7px 2px' }}>
        <span>시각</span><span>사용자</span><span>작업 · 대상</span><span style={{ textAlign: 'right' }}>출처</span>
      </div>
      {auditList().map(a => (
        <div key={a.id} className="trow" style={{ gridTemplateColumns: GRID, gap: 12, padding: '9px 2px', fontSize: 12.5 }}>
          <span className="muted2" style={{ fontSize: 11.5 }}>{a.at}</span>
          <span style={{ fontWeight: 600 }}>{a.actor}</span>
          <span style={{ minWidth: 0 }}><code className="code">{a.action}</code> <span className="muted">{a.target}</span></span>
          <span style={{ textAlign: 'right' }}>
            <span className="badge" style={{ fontSize: 10.5, fontWeight: 800, padding: '2px 8px', background: a.source === 'MCP' ? 'rgba(107,91,210,.12)' : 'var(--chip)', color: a.source === 'MCP' ? '#6b5bd2' : 'var(--muted)' }}>{a.source}</span>
          </span>
        </div>
      ))}
    </section>
  )
}

function McpTab() {
  return (
    <section className="card">
      <h2 style={{ marginBottom: 4 }}>MCP 도구 카탈로그</h2>
      <div className="muted2" style={{ fontSize: 12, marginBottom: 14 }}>AI 어시스턴트에 노출되는 도구 — 이 목록에 없는 것은 호출할 수 없습니다.</div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 12 }}>
        {MCP_TOOLS.map(t => (
          <div key={t.name} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 10, background: 'var(--soft)', border: '1px solid var(--border-soft)', borderRadius: 10, padding: '13px 15px' }}>
            <div style={{ minWidth: 0 }}>
              <code style={{ fontFamily: 'ui-monospace,Menlo,monospace', fontSize: 12, fontWeight: 600 }}>{t.name}</code>
              <div className="muted2" style={{ fontSize: 11.5, marginTop: 3 }}>{t.desc}</div>
            </div>
            <span className="badge" style={{ flex: 'none', fontSize: 10.5, fontWeight: 700, padding: '2px 9px', background: t.scope === '쓰기' ? 'rgba(185,130,15,.13)' : 'var(--primary-soft)', color: t.scope === '쓰기' ? '#b9820f' : 'var(--primary-2)' }}>{t.scope}</span>
          </div>
        ))}
      </div>
    </section>
  )
}

function UsersTab() {
  const st = useStore()
  const { S, setState, people, userEditCommit, openUserCreate, openUserEdit } = st
  const GRID = 'minmax(110px,1.3fr) 96px 68px 88px 112px'
  const kw = S.userKw.trim()
  const ue = S.userEdit
  const setUE = (k) => (e) => setState(s => ({ userEdit: { ...s.userEdit, [k]: e.target.value } }))
  const leafTeams = teamOptions(st)
  const ALL = people()

  // ── 좌측 조직 트리 (선택 노드 + 하위 팀 범위 필터, 프로토타입 사양) ──
  const descNames = (node) => [node.name, ...(node.children || []).flatMap(descNames)]
  const sel = S.userTeamSel
  const pick = (name) => setState({ userTeamSel: sel === name ? null : name, userEdit: null })
  const cntFor = (names) => ALL.filter(p => names.includes(p.team)).length
  const treeRows = [{ pad: 0, name: '전체', label: '전체', cnt: ALL.length, weight: 700, on: sel == null, onClick: () => setState({ userTeamSel: null, userEdit: null }) }]
  treeRows.push({ pad: 0, name: S.companyName, label: `${S.companyName} (전사 직속)`, cnt: cntFor([S.companyName]), weight: 700, on: sel === S.companyName, onClick: () => pick(S.companyName) })
  const walk = (n, depth) => {
    treeRows.push({ pad: depth * 14, name: n.name, label: n.name, cnt: cntFor(descNames(n)), weight: (n.children || []).length ? 700 : 500, on: sel === n.name, onClick: () => pick(n.name) })
    ;(n.children || []).forEach(c => walk(c, depth + 1))
  }
  S.orgTree.forEach(n => walk(n, 1))

  let scopeNames = null
  if (sel != null) {
    const find = (ns) => { for (const n of ns) { if (n.name === sel) return n; const f = find(n.children || []); if (f) return f } return null }
    const node = find(S.orgTree)
    scopeNames = node ? descNames(node) : [sel]
  }
  const P = ALL.filter(p => (!scopeNames || scopeNames.includes(p.team)) && (!kw || p.name.includes(kw) || p.team.includes(kw)))

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '190px minmax(420px,1fr)', gap: 16, alignItems: 'start' }}>
      <section className="card" style={{ padding: '14px 10px' }}>
        <div className="muted2" style={{ fontSize: 11, fontWeight: 700, letterSpacing: '.4px', padding: '0 8px 8px' }}>조직</div>
        <div style={{ display: 'grid', gap: 1 }}>
          {treeRows.map(tn => (
            <button key={tn.name} onClick={tn.onClick} className="org-node"
              style={{ paddingLeft: 8 + tn.pad, fontWeight: tn.weight, background: tn.on ? 'var(--primary-soft)' : 'transparent', color: tn.on ? 'var(--primary)' : 'var(--muted)' }}>
              <span style={{ flex: 1, minWidth: 0, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{tn.label}</span>
              <span className="muted2" style={{ flex: 'none', fontSize: 11, fontWeight: 600 }}>{tn.cnt}</span>
            </button>
          ))}
        </div>
      </section>

      <section className="card">
      <div className="card-head">
        <h2>{sel == null ? '전체' : sel} <span className="muted2" style={{ fontWeight: 500, fontSize: 12.5 }}>{P.length}명</span></h2>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <input placeholder="이름 · 팀 검색" value={S.userKw} onChange={e => setState({ userKw: e.target.value })} style={{ width: 200 }} />
          <button className="btn btn-primary" onClick={openUserCreate}>+ 사용자 추가</button>
        </div>
      </div>
      <div className="thead" style={{ gridTemplateColumns: GRID }}>
        <span>이름</span><span>팀</span><span>직급</span><span>권한</span><span style={{ textAlign: 'right' }}>관리</span>
      </div>
      {P.map(p => {
        if (ue && ue.id === p.id) {
          return (
            <div key={p.id} className="trow" style={{ gridTemplateColumns: GRID, padding: '5px 2px' }}>
              <input value={ue.name} onChange={setUE('name')} autoFocus placeholder="이름"
                onKeyDown={e => { if (e.key === 'Enter') userEditCommit(); if (e.key === 'Escape') setState({ userEdit: null }) }}
                style={{ fontSize: 12.5, padding: '4px 8px', borderColor: 'var(--primary)' }} />
              <select value={ue.team} onChange={setUE('team')} style={{ fontSize: 12, padding: '4px 6px', borderColor: 'var(--primary)' }}>
                {leafTeams.map(o => <option key={o.name} value={o.name}>{o.label}</option>)}
              </select>
              <select value={ue.grade} onChange={setUE('grade')} style={{ fontSize: 12, padding: '4px 6px', borderColor: 'var(--primary)' }}>
                {S.grades.map(g => <option key={g.name} value={g.name}>{g.name}</option>)}
              </select>
              <select value={ue.orgRole} onChange={setUE('orgRole')} style={{ fontSize: 12, padding: '4px 6px', borderColor: 'var(--primary)' }}>
                {S.roles.map(r => <option key={r.key} value={r.key}>{r.label}</option>)}
              </select>
              <span style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                <button className="btn btn-primary btn-sm" onClick={userEditCommit}>저장</button>
                <button className="btn btn-ghost btn-sm" onClick={() => setState({ userEdit: null })}>취소</button>
              </span>
            </div>
          )
        }
        const rd = S.roles.find(x => x.key === p.orgRole)
        const [bg, c] = roleColor(p.orgRole)
        return (
          <div key={p.id} className="trow" style={{ gridTemplateColumns: GRID, padding: '8px 2px' }}>
            <span style={{ minWidth: 0, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              <strong>{p.name}</strong> <span className="muted2" style={{ fontSize: 12 }}>{p.title}</span>
            </span>
            <span className="muted" style={{ fontSize: 12.5 }}>{p.team}</span>
            <span className="muted" style={{ fontSize: 12.5 }}>{p.grade}</span>
            <span><span className="badge" style={{ fontSize: 10.5, fontWeight: 700, background: bg, color: c }}>{rd ? rd.label : p.orgRole}</span></span>
            <span style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
              <button className="btn btn-ghost btn-sm" onClick={() => setState({ userEdit: { id: p.id, name: p.name, team: p.team, grade: p.grade, orgRole: p.orgRole }, userModal: null })}>수정</button>
              <button className="btn btn-danger-ghost btn-sm" onClick={() => setState({ userDelConfirm: p.id })}>삭제</button>
            </span>
          </div>
        )
      })}
      {P.length === 0 && <div className="empty">해당 조직에 인원이 없습니다.</div>}
      </section>
    </div>
  )
}

/** 조직 트리 → 셀렉트 옵션 (들여쓰기 포함) */
export function teamOptions(st) {
  const out = []
  const walk = (ns, depth) => ns.forEach(n => { out.push({ name: n.name, label: '　'.repeat(depth) + n.name }); walk(n.children || [], depth + 1) })
  walk(st.S.orgTree, 0)
  return out
}

function OrgTab() {
  const st = useStore()
  const { S, setState, people, projects, orgCommit, companyCommit, orgDelete, gradeCommit, gradeDelete, roleCommit, roleDelete, togglePerm } = st
  const P = people()
  const DIV_ICON = 'M3 7a2 2 0 012-2h4l2 2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z'
  const TEAM_ICON = 'M17 21v-2a4 4 0 00-4-4H7a4 4 0 00-4 4v2M14 7a4 4 0 11-8 0 4 4 0 018 0'

  const orgInput = (pad, ph, key) => {
    const act = S.orgAdd || S.orgEdit
    return (
      <div key={key} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 0', marginLeft: pad }}>
        <input value={act ? act.val : ''} placeholder={ph} autoFocus
          onChange={e => setState(s => s.orgAdd ? { orgAdd: { ...s.orgAdd, val: e.target.value } } : { orgEdit: { ...s.orgEdit, val: e.target.value } })}
          onKeyDown={e => { if (e.key === 'Enter') orgCommit(); if (e.key === 'Escape') setState({ orgAdd: null, orgEdit: null }) }}
          style={{ width: 220, borderColor: 'var(--primary)' }} />
        <span style={{ flex: 1 }} />
        <button className="btn btn-primary btn-sm" style={{ padding: '7px 12px', fontSize: 12 }} onClick={orgCommit}>저장</button>
        <button className="btn btn-ghost btn-sm" style={{ padding: '7px 12px', fontSize: 12 }} onClick={() => setState({ orgAdd: null, orgEdit: null })}>취소</button>
      </div>
    )
  }

  const orgRows = []
  const walkOrg = (ns, depth) => {
    ns.forEach(n => {
      const pad = depth * 24
      const kids = n.children || []
      if (S.orgEdit && S.orgEdit.id === n.id) orgRows.push(orgInput(pad, '조직 이름', `e${n.id}`))
      else {
        const ocnt = P.filter(p => p.team === n.name).length
        const pcnt = projects().filter(p => p.team === n.name).length
        const hasKids = kids.length > 0
        orgRows.push(
          <div key={n.id} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '7px 0', marginLeft: pad, borderBottom: '1px solid var(--border-soft)' }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke={hasKids ? '#6b5bd2' : 'var(--muted)'} strokeWidth="2"><path d={hasKids ? DIV_ICON : TEAM_ICON} /></svg>
            <span style={{ fontSize: 13, fontWeight: hasKids ? 700 : 600, whiteSpace: 'nowrap' }}>{n.name}</span>
            <span className="muted2" style={{ fontSize: 11.5, whiteSpace: 'nowrap' }}>{(hasKids ? `하위 ${kids.length} · ` : '') + `인원 ${ocnt} · 프로젝트 ${pcnt}`}</span>
            <span style={{ flex: 1 }} />
            <button className="btn btn-ghost btn-sm" style={{ color: 'var(--primary)' }} onClick={() => setState({ orgAdd: { parentId: n.id, val: '' }, orgEdit: null })}>+ 하위</button>
            <button className="btn btn-ghost btn-sm" onClick={() => setState({ orgEdit: { id: n.id, val: n.name }, orgAdd: null })}>이름 변경</button>
            <button className="btn btn-danger-ghost btn-sm" onClick={() => orgDelete(n.id)}>삭제</button>
          </div>
        )
      }
      walkOrg(kids, depth + 1)
      if (S.orgAdd && S.orgAdd.parentId === n.id) orgRows.push(orgInput((depth + 1) * 24, '새 하위 조직 이름', `a${n.id}`))
    })
  }
  walkOrg(S.orgTree, 0)
  if (S.orgAdd && S.orgAdd.parentId == null) orgRows.push(orgInput(0, '새 조직 이름', 'aroot'))

  const gf = S.gradeForm, rf = S.roleForm
  const GRADE_GRID = 'minmax(0,1fr) 64px 48px 104px'

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(420px, 1fr))', gap: 16, alignItems: 'start' }}>
      <section className="card">
        <h2 style={{ marginBottom: 4 }}>조직 구조 <DemoBadge /></h2>
        <div className="muted2" style={{ fontSize: 12, marginBottom: 14 }}>서버 인원 데이터에서 파생된 조직도입니다. 편집은 로컬 데모 — 백엔드 조직 엔티티 연동은 다음 슬라이스.</div>
        {S.companyEdit != null ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 0', borderBottom: '1px solid var(--border-soft)' }}>
            <input value={S.companyEdit} placeholder="회사 이름" autoFocus
              onChange={e => setState({ companyEdit: e.target.value })}
              onKeyDown={e => { if (e.key === 'Enter') companyCommit(); if (e.key === 'Escape') setState({ companyEdit: null }) }}
              style={{ width: 220, borderColor: 'var(--primary)' }} />
            <span style={{ flex: 1 }} />
            <button className="btn btn-primary btn-sm" style={{ padding: '7px 12px', fontSize: 12 }} onClick={companyCommit}>저장</button>
            <button className="btn btn-ghost btn-sm" style={{ padding: '7px 12px', fontSize: 12 }} onClick={() => setState({ companyEdit: null })}>취소</button>
          </div>
        ) : (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '7px 0', borderBottom: '1px solid var(--border-soft)' }}>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" strokeWidth="2"><path d="M3 21h18M5 21V5a2 2 0 012-2h10a2 2 0 012 2v16M9 8h1M9 12h1M14 8h1M14 12h1M10 21v-4h4v4" /></svg>
            <span style={{ fontSize: 13.5, fontWeight: 800, whiteSpace: 'nowrap' }}>{S.companyName}</span>
            <span className="muted2" style={{ fontSize: 11.5 }}>회사</span>
            <span style={{ flex: 1 }} />
            <button className="btn btn-ghost btn-sm" style={{ color: 'var(--primary)' }} onClick={() => setState({ orgAdd: { parentId: null, val: '' }, orgEdit: null, companyEdit: null })}>+ 하위</button>
            <button className="btn btn-ghost btn-sm" onClick={() => setState({ companyEdit: S.companyName, orgAdd: null, orgEdit: null })}>이름 변경</button>
          </div>
        )}
        {orgRows}
      </section>

      <div style={{ display: 'grid', gap: 16 }}>
        {/* 직급 관리 */}
        <section className="card" style={{ padding: '18px 20px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
            <h2 style={{ fontSize: 14.5 }}>직급 관리 <DemoBadge /></h2>
            <button className="btn btn-primary btn-sm" style={{ padding: '6px 12px', fontSize: 12 }} onClick={() => setState({ gradeForm: { mode: 'create', id: null, name: '', factor: '1' }, roleForm: null })}>+ 직급</button>
          </div>
          <div className="muted2" style={{ fontSize: 11.5, marginBottom: 10 }}>보정계수는 가동률 계산에 사용됩니다.</div>
          <div className="thead" style={{ gridTemplateColumns: GRADE_GRID, gap: 8, padding: '6px 0', fontSize: 11 }}>
            <span>직급</span><span>보정계수</span><span>인원</span><span style={{ textAlign: 'right' }}>관리</span>
          </div>
          {S.grades.map(g => {
            const cnt = `${P.filter(p => p.grade === g.name).length}명`
            if (gf && gf.mode === 'edit' && gf.id === g.id) return <div key={g.id}>{gradeInputRow(cnt)}</div>
            return (
              <div key={g.id} className="trow" style={{ gridTemplateColumns: GRADE_GRID, gap: 8, padding: '7px 0', fontSize: 12.5 }}>
                <span style={{ fontWeight: 600 }}>{g.name}</span>
                <span className="muted">×{g.factor}</span>
                <span className="muted2" style={{ fontSize: 12 }}>{cnt}</span>
                <span style={{ display: 'flex', gap: 5, justifyContent: 'flex-end' }}>
                  <button className="btn btn-ghost btn-sm" onClick={() => setState({ gradeForm: { mode: 'edit', id: g.id, name: g.name, factor: String(g.factor) }, roleForm: null })}>수정</button>
                  <button className="btn btn-danger-ghost btn-sm" onClick={() => gradeDelete(g.id)}>삭제</button>
                </span>
              </div>
            )
          })}
          {gf && gf.mode === 'create' && gradeInputRow('')}
        </section>

        {/* 권한 관리 */}
        <section className="card" style={{ padding: '18px 20px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
            <h2 style={{ fontSize: 14.5 }}>권한 관리 <DemoBadge /></h2>
            <button className="btn btn-primary btn-sm" style={{ padding: '6px 12px', fontSize: 12 }} onClick={() => setState({ roleForm: { mode: 'create', key: null, label: '', desc: '' }, gradeForm: null })}>+ 권한</button>
          </div>
          <div className="muted2" style={{ fontSize: 11.5, marginBottom: 10 }}>권한 ▾에서 권한별 기능을 켜고 끌 수 있으며 즉시 반영됩니다. 인원이 있는 권한은 삭제할 수 없습니다.</div>
          {S.roles.map(rd => {
            if (rf && rf.mode === 'edit' && rf.key === rd.key) return <div key={rd.key}>{roleInputRow()}</div>
            const [bg, c] = roleColor(rd.key)
            const open = S.roleOpen === rd.key
            return (
              <div key={rd.key}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 0', borderBottom: '1px solid var(--border-soft)' }}>
                  <span className="badge" style={{ flex: 'none', fontSize: 10.5, fontWeight: 700, background: bg, color: c }}>{rd.label}</span>
                  <span className="muted2" style={{ minWidth: 0, flex: 1, fontSize: 11.5, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{rd.desc}</span>
                  <span className="muted2" style={{ flex: 'none', fontSize: 12 }}>{P.filter(p => p.orgRole === rd.key).length}명</span>
                  <span style={{ display: 'flex', gap: 5 }}>
                    <button className="btn btn-ghost btn-sm" style={{ color: 'var(--primary)' }} onClick={() => setState({ roleOpen: open ? null : rd.key })}>{open ? '권한 ▴' : '권한 ▾'}</button>
                    <button className="btn btn-ghost btn-sm" onClick={() => setState({ roleForm: { mode: 'edit', key: rd.key, label: rd.label, desc: rd.desc }, gradeForm: null })}>수정</button>
                    {!rd.fixed && <button className="btn btn-danger-ghost btn-sm" onClick={() => roleDelete(rd.key)}>삭제</button>}
                  </span>
                </div>
                {open && (
                  <div style={{ background: 'var(--soft)', border: '1px solid var(--border-soft)', borderRadius: 10, padding: '11px 14px', margin: '8px 0 10px', display: 'grid', gap: 9 }}>
                    {PERM_DEFS.map(([pk, label, desc]) => (
                      <div key={pk} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ fontSize: 12.5, fontWeight: 600 }}>{label}</div>
                          <div className="muted2" style={{ fontSize: 11 }}>{desc}</div>
                        </div>
                        <button className={`switch ${rd.perms[pk] ? 'on' : ''}`} onClick={() => togglePerm(rd.key, pk)} aria-label={label}>
                          <span className="knob" />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )
          })}
          {rf && rf.mode === 'create' && roleInputRow()}
        </section>
      </div>
    </div>
  )

  // 렌더 헬퍼(컴포넌트 아님) — 렌더마다 리마운트되지 않아 한글 IME 입력이 안전하다
  function gradeInputRow(cnt) {
    return (
      <div className="trow" style={{ gridTemplateColumns: GRADE_GRID, gap: 8, padding: '4px 0' }}>
        <input value={gf.name} placeholder="직급명" autoFocus
          onChange={e => setState(s => ({ gradeForm: { ...s.gradeForm, name: e.target.value } }))}
          onKeyDown={e => { if (e.key === 'Enter') gradeCommit(); if (e.key === 'Escape') setState({ gradeForm: null }) }}
          style={{ fontSize: 12.5, padding: '5px 8px', borderColor: 'var(--primary)' }} />
        <input type="number" step="0.05" min="0.1" max="5" value={gf.factor} placeholder="계수"
          onChange={e => setState(s => ({ gradeForm: { ...s.gradeForm, factor: e.target.value } }))}
          onKeyDown={e => { if (e.key === 'Enter') gradeCommit(); if (e.key === 'Escape') setState({ gradeForm: null }) }}
          style={{ fontSize: 12.5, padding: '5px 6px', borderColor: 'var(--primary)' }} />
        <span className="muted2" style={{ fontSize: 12 }}>{cnt}</span>
        <span style={{ display: 'flex', gap: 5, justifyContent: 'flex-end' }}>
          <button className="btn btn-primary btn-sm" onClick={gradeCommit}>저장</button>
          <button className="btn btn-ghost btn-sm" onClick={() => setState({ gradeForm: null })}>취소</button>
        </span>
      </div>
    )
  }

  function roleInputRow() {
    return (
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '5px 0', borderBottom: '1px solid var(--border-soft)' }}>
        <input value={rf.label} placeholder="권한 이름" autoFocus
          onChange={e => setState(s => ({ roleForm: { ...s.roleForm, label: e.target.value } }))}
          onKeyDown={e => { if (e.key === 'Enter') roleCommit(); if (e.key === 'Escape') setState({ roleForm: null }) }}
          style={{ fontSize: 12.5, padding: '5px 8px', width: 96, flex: 'none', borderColor: 'var(--primary)' }} />
        <input value={rf.desc} placeholder="설명"
          onChange={e => setState(s => ({ roleForm: { ...s.roleForm, desc: e.target.value } }))}
          onKeyDown={e => { if (e.key === 'Enter') roleCommit(); if (e.key === 'Escape') setState({ roleForm: null }) }}
          style={{ fontSize: 11.5, padding: '5px 8px', flex: 1, minWidth: 0, borderColor: 'var(--primary)' }} />
        <span style={{ display: 'flex', gap: 5, flex: 'none' }}>
          <button className="btn btn-primary btn-sm" onClick={roleCommit}>저장</button>
          <button className="btn btn-ghost btn-sm" onClick={() => setState({ roleForm: null })}>취소</button>
        </span>
      </div>
    )
  }
}
