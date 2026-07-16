import { useStore } from '../store.jsx'
import { teamOptions } from '../views/Settings.jsx'
import { HQ_TEAM } from '../constants.js'

/** 프로젝트 생성/수정 + 삭제 확인 + 사용자 추가/수정/삭제 + 토스트 */
export default function Modals() {
  return (<><ProjectModal /><DelConfirm /><UserModal /><UserDelConfirm /><Toast /></>)
}

function Field({ label, children }) {
  return <div className="field"><label>{label}</label>{children}</div>
}

function ProjectModal() {
  const st = useStore()
  const { S, setState, setForm, saveProjModal, people, proj } = st
  const m = S.projModal
  if (!m) return null
  const f = m.form
  const pmPeople = people().filter(p => p.team !== HQ_TEAM)
  const teams = teamOptions(st)

  return (
    <div className="overlay">
      <div className="modal" style={{ width: 540 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18 }}>
          <h3>{m.mode === 'edit' ? '프로젝트 정보 수정' : '새 프로젝트'}</h3>
          <button style={{ background: 'none', border: 'none', color: 'var(--muted2)', fontSize: 18, lineHeight: 1, padding: '2px 6px' }} onClick={() => setState({ projModal: null })}>×</button>
        </div>
        <div style={{ display: 'grid', gap: 14 }}>
          <Field label="프로젝트명"><input value={f.name} placeholder="예: 우리은행 오픈뱅킹 구축" onChange={e => setForm('name', e.target.value)} /></Field>
          <Field label="고객사"><input value={f.client} placeholder="예: 우리은행" onChange={e => setForm('client', e.target.value)} /></Field>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label="상태">
              <select value={f.status} onChange={e => setForm('status', e.target.value)}>
                {['계약대기', '수주확정', '진행중', '완료', '유지보수중'].map(s => <option key={s} value={s}>{s}</option>)}
              </select>
            </Field>
            <Field label="수행팀">
              <select value={f.team} onChange={e => setForm('team', e.target.value)}>
                {teams.map(t => <option key={t.name} value={t.name}>{t.label}</option>)}
              </select>
            </Field>
          </div>
          <Field label="담당 PM">
            <select value={String(f.pmId)} onChange={e => setForm('pmId', e.target.value)}>
              {pmPeople.map(p => <option key={p.id} value={p.id}>{p.name} · {p.team} {p.grade}</option>)}
            </select>
          </Field>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label="시작일"><input type="date" value={f.start} onChange={e => setForm('start', e.target.value)} /></Field>
            <Field label="종료일"><input type="date" value={f.end} onChange={e => setForm('end', e.target.value)} /></Field>
          </div>
          {m.err && <div className="form-err">{m.err}</div>}
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 20 }}>
          <button className="btn btn-ghost" onClick={() => setState({ projModal: null })}>취소</button>
          <button className="btn btn-primary" onClick={saveProjModal}>{m.mode === 'edit' ? '저장' : '생성'}</button>
        </div>
      </div>
    </div>
  )
}

function DelConfirm() {
  const { S, setState, proj, deleteProj } = useStore()
  if (!S.delConfirm) return null
  const name = (proj(S.delConfirm) || {}).name || ''
  return (
    <div className="overlay" style={{ zIndex: 95 }}>
      <div className="modal" style={{ width: 420 }}>
        <h3 style={{ color: 'var(--danger)', marginBottom: 8 }}>프로젝트 삭제</h3>
        <div style={{ fontSize: 13.5, lineHeight: 1.6, marginBottom: 18 }}>
          [{name}] 프로젝트를 삭제할까요?<br />
          <span className="muted2" style={{ fontSize: 12.5 }}>배정·유지보수 이력 표시가 함께 사라지며, 되돌릴 수 없습니다.</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <button className="btn btn-ghost" onClick={() => setState({ delConfirm: null })}>취소</button>
          <button className="btn btn-danger" onClick={() => deleteProj(S.delConfirm)}>삭제</button>
        </div>
      </div>
    </div>
  )
}

function UserModal() {
  const st = useStore()
  const { S, setState, setUForm, saveUserModal } = st
  const m = S.userModal
  if (!m) return null
  const f = m.form
  const teams = teamOptions(st)

  return (
    <div className="overlay">
      <div className="modal" style={{ width: 460 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18 }}>
          <h3>{m.mode === 'edit' ? '사용자 정보 수정' : '사용자 추가'}</h3>
          <button style={{ background: 'none', border: 'none', color: 'var(--muted2)', fontSize: 18, lineHeight: 1, padding: '2px 6px' }} onClick={() => setState({ userModal: null })}>×</button>
        </div>
        <div style={{ display: 'grid', gap: 14 }}>
          <Field label="이름"><input value={f.name} placeholder="예: 홍길동" onChange={e => setUForm('name', e.target.value)} /></Field>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label="소속 팀">
              <select value={f.team} onChange={e => setUForm('team', e.target.value)}>
                {teams.map(t => <option key={t.name} value={t.name}>{t.label}</option>)}
                <option value={HQ_TEAM}>{HQ_TEAM} (전사 직속)</option>
              </select>
            </Field>
            <Field label="직급">
              <select value={f.grade} onChange={e => setUForm('grade', e.target.value)}>
                {S.grades.map(g => <option key={g.name} value={g.name}>{g.name}</option>)}
              </select>
            </Field>
          </div>
          <Field label="권한">
            <select value={f.orgRole} onChange={e => setUForm('orgRole', e.target.value)}>
              {S.roles.map(r => <option key={r.key} value={r.key}>{r.label}</option>)}
            </select>
          </Field>
          {m.err && <div className="form-err">{m.err}</div>}
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 20 }}>
          <button className="btn btn-ghost" onClick={() => setState({ userModal: null })}>취소</button>
          <button className="btn btn-primary" onClick={saveUserModal}>{m.mode === 'edit' ? '저장' : '추가'}</button>
        </div>
      </div>
    </div>
  )
}

function UserDelConfirm() {
  const { S, setState, people, deleteUser } = useStore()
  if (!S.userDelConfirm) return null
  const name = (people().find(x => x.id === S.userDelConfirm) || {}).name || ''
  return (
    <div className="overlay" style={{ zIndex: 95 }}>
      <div className="modal" style={{ width: 420 }}>
        <h3 style={{ color: 'var(--danger)', marginBottom: 8 }}>사용자 삭제</h3>
        <div style={{ fontSize: 13.5, lineHeight: 1.6, marginBottom: 18 }}>
          {name}님을 삭제할까요?<br />
          <span className="muted2" style={{ fontSize: 12.5 }}>가동률·배정 화면에서 함께 사라지며, 되돌릴 수 없습니다.</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <button className="btn btn-ghost" onClick={() => setState({ userDelConfirm: null })}>취소</button>
          <button className="btn btn-danger" onClick={() => deleteUser(S.userDelConfirm)}>삭제</button>
        </div>
      </div>
    </div>
  )
}

function Toast() {
  const { S } = useStore()
  if (!S.toast) return null
  return <div className="toast">{S.toast}</div>
}
