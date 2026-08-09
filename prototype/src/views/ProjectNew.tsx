// 프로젝트 등록 — 참여자별 role(PM/PL/참여자) 선택, PM 1명 필수 · 422/409 오류 표시
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { createProject, useApp } from '../core/store'
import { orgCanCreateProject } from '../core/permissions'
import { ErrorBox, Field, RoleBadge } from '../components/ui'
import { ENGAGEMENT_LABEL } from '../types'
import type { Project, ProjectRole } from '../types'

export default function ProjectNew() {
  const s = useApp()
  const nav = useNavigate()
  const me = s.people.find((p) => p.id === s.currentUserId)!
  const [name, setName] = useState('')
  const [client, setClient] = useState('')
  const [solution, setSolution] = useState('검색엔진')
  const [engagement, setEngagement] = useState<Project['engagement']>('REMOTE')
  const [contractMm, setContractMm] = useState(1)
  const [startDate, setStartDate] = useState('2026-09-01')
  const [endDate, setEndDate] = useState('2026-12-31')
  const [members, setMembers] = useState<{ personId: number; role: ProjectRole }[]>([{ personId: me.id, role: 'PM' }])
  const [pick, setPick] = useState(0)
  const [msg, setMsg] = useState('')

  if (!orgCanCreateProject(me, s.roleGroups)) {
    return <ErrorBox>소속 권한 그룹에 프로젝트 생성 권한이 없습니다. (서버도 403으로 거절합니다)</ErrorBox>
  }

  const addMember = () => {
    if (!pick || members.some((m) => m.personId === pick)) return
    setMembers([...members, { personId: pick, role: 'PARTICIPANT' }])
    setPick(0)
  }
  const submit = () => {
    const r = createProject({ name, client, solution, engagement, contractMm, startDate, endDate, members })
    if (!r.ok) { setMsg(`${r.code}: ${r.message}`); return }
    nav(`/projects/${r.data}`)
  }

  return (
    <>
      <h1 className="page">프로젝트 등록</h1>
      <p className="page-desc">생성 시 상태는 '계약대기'로 시작합니다. PM 1명 지정이 필수입니다(본인이 아니어도 됨).</p>
      <div className="card" style={{ maxWidth: 720 }}>
        <div className="grid cols-2">
          <Field label="프로젝트명 *"><input value={name} onChange={(e) => setName(e.target.value)} /></Field>
          <Field label="고객사 *"><input value={client} onChange={(e) => setClient(e.target.value)} /></Field>
          <Field label="제품군(솔루션)"><input value={solution} onChange={(e) => setSolution(e.target.value)} /></Field>
          <Field label="수행 형태">
            <select value={engagement} onChange={(e) => setEngagement(e.target.value as Project['engagement'])}>
              {(Object.keys(ENGAGEMENT_LABEL) as Project['engagement'][]).map((k) => (
                <option key={k} value={k}>{ENGAGEMENT_LABEL[k]}</option>
              ))}
            </select>
          </Field>
          <Field label="계약 M/M"><input type="number" step="0.5" value={contractMm} onChange={(e) => setContractMm(Number(e.target.value))} /></Field>
          <div className="grid cols-2">
            <Field label="시작일"><input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} /></Field>
            <Field label="종료일"><input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} /></Field>
          </div>
        </div>
        <Field label="참여자 · 역할 (PM 1명 필수)">
          <table>
            <tbody>
              {members.map((m, i) => {
                const p = s.people.find((x) => x.id === m.personId)!
                return (
                  <tr key={m.personId}>
                    <td>{p.name} <span style={{ color: 'var(--muted)', fontSize: 12 }}>({p.team})</span></td>
                    <td>
                      <select value={m.role} onChange={(e) => {
                        const next = [...members]
                        next[i] = { ...m, role: e.target.value as ProjectRole }
                        setMembers(next)
                      }}>
                        <option value="PM">PM</option><option value="PL">PL</option><option value="PARTICIPANT">참여자</option>
                      </select>
                    </td>
                    <td><RoleBadge role={m.role} /></td>
                    <td><button className="btn sm danger" onClick={() => setMembers(members.filter((x) => x.personId !== m.personId))}>제거</button></td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
            <select value={pick} onChange={(e) => setPick(Number(e.target.value))} style={{ flex: 1 }}>
              <option value={0}>인원 선택…</option>
              {s.people.filter((p) => p.active && !p.isSystem && !members.some((m) => m.personId === p.id)).map((p) => (
                <option key={p.id} value={p.id}>{p.name} — {p.team}</option>
              ))}
            </select>
            <button className="btn" onClick={addMember}>추가</button>
          </div>
        </Field>
        {msg && <ErrorBox>{msg}</ErrorBox>}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <button className="btn" onClick={() => nav('/projects')}>취소</button>
          <button className="btn primary" onClick={submit}>등록</button>
        </div>
      </div>
    </>
  )
}
