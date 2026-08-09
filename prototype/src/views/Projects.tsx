// 프로젝트 목록 — phase 탭(영업/솔루션 — status 파생, v2.4) · 필터 · 검색 · 페이지네이션
import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useApp } from '../core/store'
import { phaseOf } from '../core/state'
import { visibleProjects } from '../core/visibility'
import { orgCanCreateProject } from '../core/permissions'
import { Empty, Pagination, ProgressBar, StatusBadge } from '../components/ui'
import type { Phase, ProjectStatus } from '../types'

const PAGE_SIZE = 15

export default function Projects() {
  const s = useApp()
  const nav = useNavigate()
  const me = s.people.find((p) => p.id === s.currentUserId)!
  const [phase, setPhase] = useState<Phase>('SOLUTION')
  const [status, setStatus] = useState<ProjectStatus | ''>('')
  const [solution, setSolution] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)

  const visible = useMemo(() => visibleProjects(me, s.projects, s.assignments, s), [s, me])
  const solutions = useMemo(() => [...new Set(visible.map((p) => p.solution))].sort(), [visible])

  const filtered = visible.filter((p) =>
    phaseOf(p.status) === phase
    && (!status || p.status === status)
    && (!solution || p.solution === solution)
    && (!q || p.name.toLowerCase().includes(q.toLowerCase()) || p.client.toLowerCase().includes(q.toLowerCase())),
  )
  const totalPages = Math.ceil(filtered.length / PAGE_SIZE)
  const rows = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)
  const statusOptions: ProjectStatus[] = phase === 'SALES' ? ['계약대기', '수주확정'] : ['진행중', '완료']

  return (
    <>
      <h1 className="page">프로젝트</h1>
      <p className="page-desc">가시성 범위 내 {visible.length}건 · 유지보수중 프로젝트는 [유지보수 계약] 탭에서 계약으로 조회합니다.</p>
      <div className="tabs">
        <button className={phase === 'SALES' ? 'active' : ''} onClick={() => { setPhase('SALES'); setStatus(''); setPage(0) }}>
          영업 (계약대기·수주확정)
        </button>
        <button className={phase === 'SOLUTION' ? 'active' : ''} onClick={() => { setPhase('SOLUTION'); setStatus(''); setPage(0) }}>
          솔루션 (진행중·완료)
        </button>
      </div>
      <div className="toolbar">
        <input placeholder="이름·고객사 검색" value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} style={{ width: 220 }} />
        <select value={status} onChange={(e) => { setStatus(e.target.value as ProjectStatus | ''); setPage(0) }}>
          <option value="">상태 전체</option>
          {statusOptions.map((x) => <option key={x} value={x}>{x}</option>)}
        </select>
        <select value={solution} onChange={(e) => { setSolution(e.target.value); setPage(0) }}>
          <option value="">제품군 전체</option>
          {solutions.map((x) => <option key={x} value={x}>{x}</option>)}
        </select>
        <span className="spacer" style={{ flex: 1 }} />
        {orgCanCreateProject(me, s.roleGroups) && (
          <button className="btn primary" onClick={() => nav('/projects/new')}>+ 프로젝트 등록</button>
        )}
      </div>
      <div className="card">
        {rows.length === 0 ? <Empty>조건에 맞는 프로젝트가 없습니다.</Empty> : (
          <table>
            <thead>
              <tr><th>프로젝트</th><th>고객사</th><th>제품군</th><th>PM</th><th>상태</th><th>진행률</th><th>기간</th></tr>
            </thead>
            <tbody>
              {rows.map((p) => {
                const pm = s.people.find((x) => x.id === p.managerId)
                return (
                  <tr key={p.id} className="clickable" onClick={() => nav(`/projects/${p.id}`)}>
                    <td><Link to={`/projects/${p.id}`}>{p.name}</Link></td>
                    <td>{p.client}</td>
                    <td>{p.solution}</td>
                    <td>{pm?.name}</td>
                    <td>
                      <StatusBadge status={p.status} />
                      {p.status === '완료' && <span className="badge outline" style={{ marginLeft: 6 }}>이관 대기</span>}
                    </td>
                    <td><ProgressBar value={p.progress} /></td>
                    <td style={{ fontSize: 12, color: 'var(--muted)' }}>{p.startDate} ~ {p.endDate}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
        <Pagination page={page} totalPages={totalPages} onChange={setPage} />
      </div>
    </>
  )
}
