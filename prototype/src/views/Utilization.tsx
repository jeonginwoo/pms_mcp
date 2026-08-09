// 가동률 대시보드 — 월 선택 · 팀 필터 · 기본/보정 · 과부하 강조 (EPIC C)
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { CURRENT_MONTH, useApp } from '../core/store'
import { isOverbooked, utilizationFor } from '../core/utilization'
import { orgScopePeople } from '../core/visibility'
import { Empty } from '../components/ui'

export default function Utilization() {
  const s = useApp()
  const me = s.people.find((p) => p.id === s.currentUserId)!
  const [month, setMonth] = useState(CURRENT_MONTH)
  const [team, setTeam] = useState('')
  const [overOnly, setOverOnly] = useState(false)

  const scoped = useMemo(() => orgScopePeople(me, s.people.filter((p) => p.active && !p.isSystem), s), [s, me])
  const teams = useMemo(() => [...new Set(scoped.map((p) => p.team))].sort(), [scoped])
  const rows = useMemo(
    () => utilizationFor(month, scoped, s.projects, s.assignments),
    [month, scoped, s],
  )
  // 집계 모집단 = billable (C1-5) · 개인 행은 billable 무관 표시
  const billableRows = rows.filter((r) => r.person.billable)
  const avg = billableRows.length
    ? Math.round(billableRows.reduce((sum, r) => sum + r.adjusted, 0) / billableRows.length)
    : 0
  const overbooked = billableRows.filter(isOverbooked)

  const visible = rows
    .filter((r) => (!team || r.person.team === team) && (!overOnly || (r.person.billable && isOverbooked(r))))
    .sort((a, b) => b.adjusted - a.adjusted)

  return (
    <>
      <h1 className="page">가동률</h1>
      <p className="page-desc">
        기본 = Σ배정MM ÷ 가용 × 100 · 보정 = ÷(가용 × 직급계수). 집계·과부하 목록의 모집단은 billable=true 인원입니다.
      </p>
      <div className="grid cols-3">
        <div className="card"><div className="kpi">{avg}%<small>평균 보정 가동률 (billable {billableRows.length}명 · 가시성 범위)</small></div></div>
        <div className="card"><div className="kpi"><span className={overbooked.length ? 'util-over' : ''}>{overbooked.length}명</span><small>과부하 (보정 &gt; 100%)</small></div></div>
        <div className="card"><div className="kpi">{scoped.length - billableRows.length}명<small>집계 제외 (billable=false — 대표·지원조직)</small></div></div>
      </div>
      <div className="toolbar">
        <input type="month" value={month} onChange={(e) => setMonth(e.target.value)} />
        <select value={team} onChange={(e) => setTeam(e.target.value)}>
          <option value="">팀 전체</option>
          {teams.map((t) => <option key={t} value={t}>{t}</option>)}
        </select>
        <label style={{ fontSize: 13 }}>
          <input type="checkbox" checked={overOnly} onChange={(e) => setOverOnly(e.target.checked)} /> 과부하만 보기
        </label>
      </div>
      <div className="card">
        {visible.length === 0 ? <Empty>조건에 맞는 인원이 없습니다.</Empty> : (
          <table>
            <thead>
              <tr><th>이름</th><th>소속</th><th>직급 (계수)</th><th>배정 프로젝트</th><th>Σ배정 MM</th><th>기본</th><th>보정</th><th>집계</th></tr>
            </thead>
            <tbody>
              {visible.map((r) => (
                <tr key={r.person.id}>
                  <td><Link to={`/people?focus=${r.person.id}`}>{r.person.name}</Link></td>
                  <td style={{ color: 'var(--muted)' }}>{r.person.division} · {r.person.team}</td>
                  <td>{r.person.grade} ({r.person.gradeCoeff})</td>
                  <td style={{ fontSize: 12 }}>
                    {r.projects.length === 0 ? '—' : r.projects.map(({ project, mm }) => (
                      <div key={project.id}><Link to={`/projects/${project.id}`}>{project.name}</Link> ({mm}MM)</div>
                    ))}
                  </td>
                  <td>{r.totalMM.toFixed(1)}</td>
                  <td>{Math.round(r.basic)}%</td>
                  <td className={r.person.billable && isOverbooked(r) ? 'util-over' : ''}>{Math.round(r.adjusted)}%</td>
                  <td>{r.person.billable ? <span className="badge green">포함</span> : <span className="badge gray">제외</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  )
}
