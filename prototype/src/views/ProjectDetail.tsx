// 프로젝트 상세 — 개요(진행률 2단계·완료/재개·이관) · 배정 · 권한 패널(US-A8) · 이력 탭(US-G2)
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  addAssignment, changePM, closeAssignment, completeProject, deleteProject, handover,
  reopenProject, savePermissions, saveProgress, setRole, updateAssignment,
  updateProject, useApp,
} from '../core/store'
import {
  ACTION_LABEL, canDo, canDoFixed, defaultCell, effectiveCell, roleOf,
} from '../core/permissions'
import { isProjectVisible } from '../core/visibility'
import { Empty, ErrorBox, Field, InfoBox, Modal, ProgressBar, RoleBadge, StatusBadge } from '../components/ui'
import { ENGAGEMENT_LABEL } from '../types'
import type { PermAction, Project } from '../types'

export default function ProjectDetail() {
  const { id } = useParams()
  const s = useApp()
  const me = s.people.find((p) => p.id === s.currentUserId)!
  const project = s.projects.find((p) => p.id === Number(id))

  // 404 은닉 — 부재와 권한 밖을 구분하지 않는다 (A3-2)
  if (!project || !isProjectVisible(me, project, s.assignments, s)) {
    return (
      <>
        <h1 className="page">프로젝트를 찾을 수 없습니다 (404)</h1>
        <p className="page-desc">존재하지 않거나 가시성 범위 밖입니다 — 서버는 두 경우를 구분해 주지 않습니다(은닉).</p>
        <Link to="/projects">← 목록으로</Link>
      </>
    )
  }
  return <Detail project={project} />
}

function Detail({ project: p }: { project: Project }) {
  const s = useApp()
  const nav = useNavigate()
  const me = s.people.find((x) => x.id === s.currentUserId)!
  const [tab, setTab] = useState<'개요' | '배정' | '권한' | '이력'>('개요')
  const myRole = roleOf(me, p, s.assignments, s.roleGroups)
  const assigns = s.assignments.filter((a) => a.projectId === p.id && a.status === 'ACTIVE')
  const pm = s.people.find((x) => x.id === p.managerId)

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 12 }}>
        <h1 className="page">{p.name}</h1>
        <StatusBadge status={p.status} />
        {myRole && <RoleBadge role={myRole} />}
      </div>
      <p className="page-desc">
        {p.client} · {p.solution} · {ENGAGEMENT_LABEL[p.engagement]} · 계약 {p.contractMm}MM · {p.startDate} ~ {p.endDate} · PM {pm?.name}
        {p.lastEditedAt && ` · 마지막 수정 ${s.people.find((x) => x.id === p.lastEditedBy)?.name ?? ''} (${p.lastEditedAt})`}
      </p>
      <div className="tabs">
        {(['개요', '배정', '권한', '이력'] as const).map((t) => (
          <button key={t} className={tab === t ? 'active' : ''} onClick={() => setTab(t)}>{t}</button>
        ))}
      </div>
      {tab === '개요' && <Overview p={p} />}
      {tab === '배정' && <AssignPanel p={p} />}
      {tab === '권한' && <PermPanel p={p} />}
      {tab === '이력' && <AuditTab p={p} />}
    </>
  )
}

// ── 개요: 정보 수정 · 진행률 2단계 · 상태 버튼(완료/재개/이관/삭제) ──
function Overview({ p }: { p: Project }) {
  const s = useApp()
  const nav = useNavigate()
  const me = s.people.find((x) => x.id === s.currentUserId)!
  const [msg, setMsg] = useState('')
  const [info, setInfo] = useState('')
  const [showProgress, setShowProgress] = useState(false)
  const [showHandover, setShowHandover] = useState(false)
  const [showEdit, setShowEdit] = useState(false)

  const canProgress = canDo(me, p, 'PROGRESS', s.assignments, s.overrides, s.roleGroups)
  const canComplete = canDo(me, p, 'COMPLETE_REOPEN', s.assignments, s.overrides, s.roleGroups)
  const canEdit = canDo(me, p, 'EDIT_INFO', s.assignments, s.overrides, s.roleGroups)
  const isPM = canDoFixed(me, p, s.assignments, s.roleGroups)

  const run = (r: { ok: boolean; code?: string; message?: string }, okMsg?: string) => {
    if (!r.ok) { setMsg(`${(r as any).code}: ${(r as any).message}`); setInfo('') }
    else { setMsg(''); setInfo(okMsg ?? '') }
  }

  return (
    <div className="grid cols-2">
      <div className="card">
        <h3>진행률</h3>
        <ProgressBar value={p.progress} />
        <div style={{ marginTop: 14, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {/* 권한 없는 버튼은 렌더링하지 않는다 — 단 서버(스토어) 403은 항상 존재 (부록 A 공통 규칙) */}
          {canProgress && p.status !== '완료' && p.status !== '유지보수중' && (
            <button className="btn primary" onClick={() => setShowProgress(true)}>진척률 수정 (2단계 확인)</button>
          )}
          {canComplete && p.status === '진행중' && p.progress === 100 && (
            <button className="btn primary" onClick={() => run(completeProject(p.id, p.version), '완료 처리되었습니다.')}>완료 처리</button>
          )}
          {canComplete && p.status === '완료' && (
            <button className="btn" onClick={() => run(reopenProject(p.id, p.version), '재개되었습니다 — 진행률 90%로 리셋 (A7-3).')}>재개</button>
          )}
          {isPM && p.status === '완료' && (
            <button className="btn" onClick={() => setShowHandover(true)}>유지보수 이관</button>
          )}
        </div>
        {p.status === '진행중' && p.progress === 100 && (
          <InfoBox>진행률 100% — 상태는 자동으로 바뀌지 않습니다. 검수·납품이 끝났다면 <b>완료 처리</b>를 진행하세요. (A2-3)</InfoBox>
        )}
        {p.status === '완료' && (
          <InfoBox>완료 상태에서는 진척률을 직접 수정할 수 없습니다(409 PROJECT_COMPLETED). 수정하려면 재개하세요. (A2-8)</InfoBox>
        )}
        {msg && <ErrorBox>{msg}</ErrorBox>}
        {info && <InfoBox>{info}</InfoBox>}
      </div>
      <div className="card">
        <h3>정보</h3>
        <table>
          <tbody>
            <tr><th style={{ width: 110 }}>고객사</th><td>{p.client}</td></tr>
            <tr><th>제품군</th><td>{p.solution}</td></tr>
            <tr><th>수행 형태</th><td>{ENGAGEMENT_LABEL[p.engagement]}</td></tr>
            <tr><th>계약 M/M</th><td>{p.contractMm}</td></tr>
            <tr><th>기간</th><td>{p.startDate} ~ {p.endDate}</td></tr>
            <tr><th>담당 조직</th><td>{p.division} · {p.team}</td></tr>
            <tr><th>version</th><td>{p.version} <span style={{ color: 'var(--muted)', fontSize: 12 }}>(낙관적 락 — 쓰기 시 필수)</span></td></tr>
          </tbody>
        </table>
        <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
          {canEdit && p.status !== '유지보수중' && <button className="btn" onClick={() => setShowEdit(true)}>정보 수정</button>}
          {isPM && (
            <button
              className="btn danger"
              onClick={() => {
                if (confirm(`[${p.name}] 프로젝트를 삭제(soft)할까요?`)) {
                  const r = deleteProject(p.id)
                  if (r.ok) nav('/projects'); else run(r)
                }
              }}
            >삭제</button>
          )}
        </div>
      </div>
      {showProgress && <ProgressModal p={p} onClose={() => setShowProgress(false)} />}
      {showHandover && <HandoverModal p={p} onClose={() => setShowHandover(false)} />}
      {showEdit && <EditModal p={p} onClose={() => setShowEdit(false)} />}
    </div>
  )
}

// 진척률 수정 — 100% 저장(완료로 이어지는 값)만 2단계 확인, 그 외 값은 바로 저장 (피드백 #1)
// ※ 기획 변경 후보: 현행 US-A2는 모든 진척률 쓰기에 2단계를 요구 — 웹만 완화할지 PRD-pms에서 결정 필요
function ProgressModal({ p, onClose }: { p: Project; onClose: () => void }) {
  const [value, setValue] = useState(p.progress)
  const [preview, setPreview] = useState<string | null>(null)
  const [completable, setCompletable] = useState(false)
  const [msg, setMsg] = useState('')

  const commit = () => {
    const r = saveProgress(p.id, value, p.version, true)
    if (!r.ok) { setMsg(`${r.code}: ${r.message}`); setPreview(null); return }
    if (r.data.completable) setCompletable(true)
    else onClose()
  }
  const request = () => {
    if (value !== 100) { commit(); return } // 일반 값 — 확인 단계 생략
    const r = saveProgress(p.id, value, p.version, false)
    if (!r.ok) { setMsg(`${r.code}: ${r.message}`); return }
    setMsg('')
    setPreview(r.data.preview ?? '')
  }
  return (
    <Modal title="진척률 수정" onClose={onClose}>
      {completable ? (
        <>
          <InfoBox>저장되었습니다. 진행률이 100%지만 <b>상태는 그대로 '진행중'</b>입니다 — 검수·납품까지 끝났다면 상세 화면에서 완료 처리를 진행하세요. (A2-3)</InfoBox>
          <div className="actions"><button className="btn primary" onClick={onClose}>확인</button></div>
        </>
      ) : preview === null ? (
        <>
          <Field label={`진행률 (현재 ${p.progress}%) — 100% 저장 시에만 확인 단계를 거칩니다`}>
            <input type="number" min={0} max={100} value={value} onChange={(e) => setValue(Number(e.target.value))} />
          </Field>
          {msg && <ErrorBox>{msg}</ErrorBox>}
          <div className="actions">
            <button className="btn" onClick={onClose}>취소</button>
            <button className="btn primary" onClick={request}>{value === 100 ? '변경 요약 확인' : '저장'}</button>
          </div>
        </>
      ) : (
        <>
          <InfoBox><b>변경 요약(아직 저장 안 됨):</b><br />{preview}</InfoBox>
          {msg && <ErrorBox>{msg}</ErrorBox>}
          <div className="actions">
            <button className="btn" onClick={() => setPreview(null)}>돌아가기 (취소)</button>
            <button className="btn primary" onClick={commit}>확정 저장</button>
          </div>
        </>
      )}
    </Modal>
  )
}

// 정보 수정 — 등록 때 적은 정보를 모두 수정 가능 (피드백 #2)
function EditModal({ p, onClose }: { p: Project; onClose: () => void }) {
  const [form, setForm] = useState({
    name: p.name, client: p.client, solution: p.solution, engagement: p.engagement,
    contractMm: p.contractMm, startDate: p.startDate, endDate: p.endDate, status: p.status,
  })
  const [msg, setMsg] = useState('')
  const set = (k: keyof typeof form, v: unknown) => setForm({ ...form, [k]: v })
  const save = () => {
    const r = updateProject(p.id, { ...form }, p.version)
    if (!r.ok) { setMsg(`${r.code}: ${r.message}`); return }
    onClose()
  }
  return (
    <Modal title="프로젝트 정보 수정" onClose={onClose}>
      <div className="grid cols-2">
        <Field label="프로젝트명"><input value={form.name} onChange={(e) => set('name', e.target.value)} /></Field>
        <Field label="고객사"><input value={form.client} onChange={(e) => set('client', e.target.value)} /></Field>
        <Field label="제품군(솔루션)"><input value={form.solution} onChange={(e) => set('solution', e.target.value)} /></Field>
        <Field label="수행 형태">
          <select value={form.engagement} onChange={(e) => set('engagement', e.target.value)}>
            {(Object.keys(ENGAGEMENT_LABEL) as Project['engagement'][]).map((k) => (
              <option key={k} value={k}>{ENGAGEMENT_LABEL[k]}</option>
            ))}
          </select>
        </Field>
        <Field label="계약 M/M"><input type="number" step={0.5} value={form.contractMm} onChange={(e) => set('contractMm', Number(e.target.value))} /></Field>
        <Field label="상태 — 순방향 전이만(§5). 완료·재개·이관은 전용 버튼">
          <select value={form.status} onChange={(e) => set('status', e.target.value)}>
            {(['계약대기', '수주확정', '진행중', '완료', '유지보수중'] as const).map((x) => <option key={x} value={x}>{x}</option>)}
          </select>
        </Field>
        <Field label="시작일"><input type="date" value={form.startDate} onChange={(e) => set('startDate', e.target.value)} /></Field>
        <Field label="종료일"><input type="date" value={form.endDate} onChange={(e) => set('endDate', e.target.value)} /></Field>
      </div>
      {msg && <ErrorBox>{msg}</ErrorBox>}
      <div className="actions">
        <button className="btn" onClick={onClose}>취소</button>
        <button className="btn primary" onClick={save}>저장</button>
      </div>
    </Modal>
  )
}

// 유지보수 이관 — 계약 필수 정보 + 사이트(담당 엔지니어 포함) 1개 이상 (US-D1)
function HandoverModal({ p, onClose }: { p: Project; onClose: () => void }) {
  const s = useApp()
  const nav = useNavigate()
  const [contractName, setContractName] = useState(`2026 ${p.client} ${p.solution} 유지보수`)
  const [counterparty, setCounterparty] = useState(p.client)
  const [startDate, setStartDate] = useState('2026-09-01')
  const [endDate, setEndDate] = useState('2027-08-31')
  const [amount, setAmount] = useState(24_000_000)
  const [sites, setSites] = useState([{ customer: p.client, solution: p.solution, target: '솔루션' as const, serverSpec: '', engineerId: 0 }])
  const [msg, setMsg] = useState('')

  const submit = () => {
    const r = handover(p.id, { contractName, counterparty, startDate, endDate, amount, sites })
    if (!r.ok) { setMsg(`${r.code}: ${r.message}`); return }
    nav(`/maintenance/contracts/${r.data}`)
  }
  return (
    <Modal title="유지보수 이관 — 계약·사이트를 함께 생성 (한 트랜잭션)" onClose={onClose}>
      <InfoBox>필수값을 이관 시점에 받으므로 "유지보수중인데 계약 정보 없는 프로젝트"는 생기지 않습니다. (D1-1)</InfoBox>
      <div className="grid cols-2">
        <Field label="계약명 *"><input value={contractName} onChange={(e) => setContractName(e.target.value)} /></Field>
        <Field label="계약사 *"><input value={counterparty} onChange={(e) => setCounterparty(e.target.value)} /></Field>
        <Field label="시작일 *"><input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} /></Field>
        <Field label="종료일 *"><input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} /></Field>
        <Field label="연 계약금액(원) *"><input type="number" value={amount} onChange={(e) => setAmount(Number(e.target.value))} /></Field>
      </div>
      <Field label="사이트 (1개 이상 · 각 사이트 담당 엔지니어 필수)">
        {sites.map((site, i) => (
          <div key={i} style={{ display: 'flex', gap: 6, marginBottom: 6 }}>
            <input placeholder="고객사명 *" value={site.customer} onChange={(e) => { const n = [...sites]; n[i] = { ...site, customer: e.target.value }; setSites(n) }} style={{ flex: 1 }} />
            <select value={site.engineerId} onChange={(e) => { const n = [...sites]; n[i] = { ...site, engineerId: Number(e.target.value) }; setSites(n) }}>
              <option value={0}>담당 엔지니어 *</option>
              {s.people.filter((x) => x.active && !x.isSystem).map((x) => <option key={x.id} value={x.id}>{x.name}</option>)}
            </select>
            {sites.length > 1 && <button className="btn sm danger" onClick={() => setSites(sites.filter((_, j) => j !== i))}>×</button>}
          </div>
        ))}
        <button className="btn sm" onClick={() => setSites([...sites, { customer: '', solution: p.solution, target: '솔루션', serverSpec: '', engineerId: 0 }])}>+ 사이트 추가</button>
      </Field>
      {msg && <ErrorBox>{msg}</ErrorBox>}
      <div className="actions">
        <button className="btn" onClick={onClose}>취소</button>
        <button className="btn primary" onClick={submit}>이관 실행</button>
      </div>
    </Modal>
  )
}

// ── 배정 패널 — 역할 뱃지 · PM 교체 · PL 지정 · 배정 추가/종료/M/M (EPIC B · US-A6) ──
function AssignPanel({ p }: { p: Project }) {
  const s = useApp()
  const me = s.people.find((x) => x.id === s.currentUserId)!
  const [msg, setMsg] = useState('')
  const [pick, setPick] = useState(0)
  const [mm, setMm] = useState(0.5)
  const canAssign = canDo(me, p, 'ASSIGN', s.assignments, s.overrides, s.roleGroups)
  const isPM = roleOf(me, p, s.assignments, s.roleGroups) === 'PM'
  const rows = s.assignments.filter((a) => a.projectId === p.id && a.status === 'ACTIVE')

  const run = (r: { ok: boolean }) => { setMsg(r.ok ? '' : `${(r as any).code}: ${(r as any).message}`) }

  return (
    <div className="card">
      <h3>배정 인원 ({rows.length}) — 타 팀 인원도 이 프로젝트 컨텍스트 안에서는 보입니다 (A3-3)</h3>
      <table>
        <thead>
          <tr><th>이름</th><th>소속</th><th>역할</th><th>기간</th><th>월 M/M</th><th></th></tr>
        </thead>
        <tbody>
          {rows.map((a) => {
            const person = s.people.find((x) => x.id === a.personId)
            return (
              <tr key={a.id}>
                <td>{person?.name}</td>
                <td style={{ color: 'var(--muted)' }}>{person?.division} · {person?.team}</td>
                <td><RoleBadge role={a.role} /></td>
                <td style={{ fontSize: 12, color: 'var(--muted)' }}>{a.startDate} ~ {a.endDate}</td>
                <td>
                  {canAssign ? (
                    <input
                      type="number" step={0.1} min={0} max={1} value={a.monthlyMM}
                      style={{ width: 70, padding: '3px 6px' }}
                      onChange={(e) => run(updateAssignment(a.id, Number(e.target.value), a.endDate))}
                    />
                  ) : a.monthlyMM}
                </td>
                <td style={{ display: 'flex', gap: 4 }}>
                  {isPM && a.role !== 'PM' && (
                    <>
                      <button className="btn sm" onClick={() => run(changePM(p.id, a.personId))}>PM 지정</button>
                      <button className="btn sm" onClick={() => run(setRole(p.id, a.personId, a.role === 'PL' ? 'PARTICIPANT' : 'PL'))}>
                        {a.role === 'PL' ? 'PL 해제' : 'PL 지정'}
                      </button>
                      {canAssign && <button className="btn sm danger" onClick={() => run(closeAssignment(a.id))}>종료</button>}
                    </>
                  )}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
      {canAssign && (
        <div style={{ display: 'flex', gap: 8, marginTop: 12, alignItems: 'center' }}>
          <select value={pick} onChange={(e) => setPick(Number(e.target.value))} style={{ flex: 1, maxWidth: 320 }}>
            <option value={0}>배정할 인원 선택…</option>
            {s.people.filter((x) => x.active && !x.isSystem && !rows.some((a) => a.personId === x.id)).map((x) => (
              <option key={x.id} value={x.id}>{x.name} — {x.team}</option>
            ))}
          </select>
          <label style={{ fontSize: 13 }}>월 M/M <input type="number" step={0.1} min={0} max={1} value={mm} onChange={(e) => setMm(Number(e.target.value))} style={{ width: 70 }} /></label>
          <button
            className="btn primary sm"
            onClick={() => { if (pick) run(addAssignment(p.id, pick, p.startDate, p.endDate, mm)) }}
          >배정 추가</button>
        </div>
      )}
      {!canAssign && <p style={{ color: 'var(--muted)', fontSize: 12.5 }}>배정·M/M 입력 권한이 없습니다 (기본값: PM 전용 — 이 프로젝트의 권한 탭에서 PM이 PL로 위임 가능).</p>}
      {msg && <ErrorBox>{msg}</ErrorBox>}
    </div>
  )
}

// ── 권한 패널 — 역할×기능 토글 매트릭스 (US-A8) ──
// 토글은 임시 상태(draft)에만 반영되고, '변경 저장'을 눌러야 커밋·감사 1건 (피드백 #4)
const PERM_ACTIONS: PermAction[] = ['EDIT_INFO', 'ASSIGN', 'PROGRESS', 'COMPLETE_REOPEN']
const PERM_ROLES = ['PL', 'PARTICIPANT'] as const
const cellKey = (role: string, action: string) => `${role}:${action}`

function PermPanel({ p }: { p: Project }) {
  const s = useApp()
  const me = s.people.find((x) => x.id === s.currentUserId)!
  const isPM = roleOf(me, p, s.assignments, s.roleGroups) === 'PM'
  const [msg, setMsg] = useState('')

  const effective = () => {
    const m: Record<string, boolean> = {}
    for (const role of PERM_ROLES) for (const action of PERM_ACTIONS) {
      m[cellKey(role, action)] = effectiveCell(s.overrides, p.id, role, action)
    }
    return m
  }
  const [draft, setDraft] = useState<Record<string, boolean>>(effective)
  const saved = effective()
  const dirtyCount = Object.keys(draft).filter((k) => draft[k] !== saved[k]).length
  const hasCustom = s.overrides.some((o) => o.projectId === p.id)

  const save = () => {
    const cells = PERM_ROLES.flatMap((role) => PERM_ACTIONS.map((action) => ({
      role, action, allowed: draft[cellKey(role, action)],
    })))
    const r = savePermissions(p.id, cells)
    setMsg(r.ok ? '' : `${(r as any).code}: ${(r as any).message}`)
  }
  const restoreDefaults = () => {
    const m: Record<string, boolean> = {}
    for (const role of PERM_ROLES) for (const action of PERM_ACTIONS) m[cellKey(role, action)] = defaultCell(role, action)
    setDraft(m)
  }

  return (
    <div className="card" style={{ maxWidth: 820 }}>
      <h3>
        역할별 권한 — 이 프로젝트에만 적용 {hasCustom && <span className="badge purple" style={{ marginLeft: 6 }}>커스텀 적용 중</span>}
      </h3>
      <p style={{ color: 'var(--muted)', fontSize: 12.5, marginTop: 0 }}>
        기본값은 전사 공통(상위 PRD §4-2 표). PM 열·조회·삭제/이관은 고정(🔒)이며, 완료 처리·재개는 한 토글로 묶입니다.
        토글은 <b>변경 저장</b>을 눌러야 반영되고, 감사 로그도 저장 시 1건만 남습니다.
        {!isPM && ' — 조정은 PM만 가능합니다(조회는 가시성 범위).'}
      </p>
      <table className="perm-grid">
        <thead>
          <tr><th style={{ textAlign: 'left' }}>기능</th><th>PM</th><th>PL</th><th>참여자</th></tr>
        </thead>
        <tbody>
          <tr>
            <td style={{ textAlign: 'left' }}>해당 프로젝트 조회</td>
            <td><span className="lock">🔒 O</span></td><td><span className="lock">🔒 O</span></td><td><span className="lock">🔒 O</span></td>
          </tr>
          {PERM_ACTIONS.map((action) => (
            <tr key={action}>
              <td style={{ textAlign: 'left' }}>{ACTION_LABEL[action]}</td>
              <td><span className="lock">🔒 O</span></td>
              {PERM_ROLES.map((role) => {
                const k = cellKey(role, action)
                const val = draft[k]
                const isCustom = val !== defaultCell(role, action)
                const isDirty = val !== saved[k]
                return (
                  <td key={role}>
                    <button
                      className={`perm-toggle ${val ? 'on' : ''}`}
                      disabled={!isPM}
                      title={val ? '허용' : '거부'}
                      onClick={() => setDraft({ ...draft, [k]: !val })}
                    />
                    <div>
                      {isCustom && <span className="badge purple" style={{ fontSize: 10 }}>커스텀</span>}
                      {isDirty && <span className="badge amber" style={{ fontSize: 10, marginLeft: 3 }}>저장 전</span>}
                    </div>
                  </td>
                )
              })}
            </tr>
          ))}
          <tr>
            <td style={{ textAlign: 'left' }}>프로젝트 삭제 · 유지보수 이관</td>
            <td><span className="lock">🔒 O</span></td><td><span className="lock">🔒 ✕</span></td><td><span className="lock">🔒 ✕</span></td>
          </tr>
        </tbody>
      </table>
      {isPM && (
        <div style={{ marginTop: 14, display: 'flex', gap: 8 }}>
          <button className="btn primary" disabled={dirtyCount === 0} onClick={save}>
            변경 저장{dirtyCount > 0 && ` (${dirtyCount}건)`}
          </button>
          <button className="btn" disabled={dirtyCount === 0} onClick={() => setDraft(effective())}>되돌리기</button>
          <button className="btn" onClick={restoreDefaults}>기본값으로 (저장 필요)</button>
        </div>
      )}
      {msg && <ErrorBox>{msg}</ErrorBox>}
    </div>
  )
}

// ── 이력 탭 — 프로젝트 스코프 감사 이력 (US-G2, 가시성 범위 전체) ──
function AuditTab({ p }: { p: Project }) {
  const s = useApp()
  const rows = s.audit.filter((a) => a.projectId === p.id)
  return (
    <div className="card">
      <h3>변경 이력 — AuditLog 단일 원본의 projectId 필터 뷰 (참여자 포함 조회 가능)</h3>
      {rows.length === 0 ? <Empty>기록된 변경이 없습니다.</Empty> : (
        <table>
          <thead>
            <tr><th>일시</th><th>행위자</th><th>동작</th><th>대상</th><th>before</th><th>after</th><th>source</th></tr>
          </thead>
          <tbody>
            {rows.map((a) => (
              <tr key={a.id}>
                <td style={{ whiteSpace: 'nowrap' }}>{a.at}</td>
                <td>{s.people.find((x) => x.id === a.actorId)?.name}</td>
                <td><span className="badge gray">{a.action}</span></td>
                <td>{a.entityType}</td>
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
