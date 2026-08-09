// 인력 — 목록(팀 필터·검색) + 상세(참여 프로젝트·가동률). 가시성 = 조직 범위
import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { CURRENT_MONTH, useApp } from '../core/store'
import { orgScopePeople, visibleProjects } from '../core/visibility'
import { groupOf, SCOPE_LABEL } from '../core/permissions'
import { utilizationFor } from '../core/utilization'
import { Empty, RoleBadge, StatusBadge } from '../components/ui'

export default function People() {
  const s = useApp()
  const [params, setParams] = useSearchParams()
  const me = s.people.find((p) => p.id === s.currentUserId)!
  const [team, setTeam] = useState('')
  const [q, setQ] = useState('')
  const focusId = Number(params.get('focus') ?? 0)

  const scoped = useMemo(() => orgScopePeople(me, s.people.filter((p) => p.active && !p.isSystem), s), [s, me])
  const teams = useMemo(() => [...new Set(scoped.map((p) => p.team))].sort(), [scoped])
  const rows = scoped.filter((p) => (!team || p.team === team) && (!q || p.name.includes(q)))
  const focus = scoped.find((p) => p.id === focusId) ?? null

  return (
    <>
      <h1 className="page">인력</h1>
      <p className="page-desc">
        조직 가시성 범위({SCOPE_LABEL[groupOf(me, s.roleGroups).scope]})의 인원만 보입니다.
        타 팀 인원은 함께 배정된 프로젝트의 상세 화면 안에서만 보입니다(§4-4).
      </p>
      <div className="toolbar">
        <input placeholder="이름 검색" value={q} onChange={(e) => setQ(e.target.value)} style={{ width: 200 }} />
        <select value={team} onChange={(e) => setTeam(e.target.value)}>
          <option value="">팀 전체</option>
          {teams.map((t) => <option key={t} value={t}>{t}</option>)}
        </select>
      </div>
      <div className="grid cols-2">
        <div className="card">
          {rows.length === 0 ? <Empty>조건에 맞는 인원이 없습니다.</Empty> : (
            <table>
              <thead><tr><th>이름</th><th>소속</th><th>직급</th><th>권한 그룹</th></tr></thead>
              <tbody>
                {rows.map((p) => (
                  <tr key={p.id} className="clickable" onClick={() => setParams({ focus: String(p.id) })}>
                    <td><b>{p.name}</b></td>
                    <td style={{ color: 'var(--muted)' }}>{p.division} · {p.team}</td>
                    <td>{p.grade}</td>
                    <td><span className="badge gray">{groupOf(p, s.roleGroups).name}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        <div className="card">
          {!focus ? <Empty>왼쪽에서 인원을 선택하세요.</Empty> : <PersonDetail personId={focus.id} />}
        </div>
      </div>
    </>
  )
}

function PersonDetail({ personId }: { personId: number }) {
  const s = useApp()
  const me = s.people.find((p) => p.id === s.currentUserId)!
  const person = s.people.find((p) => p.id === personId)!
  const [util] = utilizationFor(CURRENT_MONTH, [person], s.projects, s.assignments)
  // 이 사람의 참여 프로젝트 중 "내 가시성" 범위 것만 노출 (프로젝트 컨텍스트 한정 원칙)
  const myVisible = new Set(visibleProjects(me, s.projects, s.assignments, s).map((p) => p.id))
  const parts = s.assignments
    .filter((a) => a.personId === personId && a.status === 'ACTIVE')
    .map((a) => ({ a, p: s.projects.find((x) => x.id === a.projectId)! }))
    .filter(({ p }) => p && !p.deleted && ['진행중', '수주확정', '계약대기'].includes(p.status))

  return (
    <>
      <h3>{person.name} <span style={{ color: 'var(--muted)', fontWeight: 400 }}>{person.division} · {person.team} · {person.grade}</span></h3>
      <div style={{ display: 'flex', gap: 24, margin: '10px 0 16px' }}>
        <div className="kpi">{Math.round(util.basic)}%<small>기본 가동률 ({CURRENT_MONTH})</small></div>
        <div className="kpi"><span className={util.adjusted > 100 ? 'util-over' : ''}>{Math.round(util.adjusted)}%</span><small>보정 (계수 {person.gradeCoeff})</small></div>
        <div className="kpi">{person.billable ? 'O' : '✕'}<small>가동률 집계 대상</small></div>
      </div>
      <h3>참여 중 프로젝트</h3>
      {parts.length === 0 ? <Empty>진행 중 배정이 없습니다.</Empty> : (
        <table>
          <thead><tr><th>프로젝트</th><th>역할</th><th>상태</th><th>월 M/M</th></tr></thead>
          <tbody>
            {parts.map(({ a, p }) => (
              <tr key={a.id}>
                <td>
                  {myVisible.has(p.id)
                    ? <Link to={`/projects/${p.id}`}>{p.name}</Link>
                    : <span style={{ color: 'var(--muted)' }}>(가시성 밖 프로젝트)</span>}
                </td>
                <td><RoleBadge role={a.role} /></td>
                <td><StatusBadge status={p.status} /></td>
                <td>{a.monthlyMM}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}
