// 홈 대시보드 — 내 프로젝트 · 내 가동률 요약 · 최근 알림 (부록 A)
import { Link } from 'react-router-dom'
import { CURRENT_MONTH, useApp } from '../core/store'
import { utilizationFor } from '../core/utilization'
import { Empty, ProgressBar, RoleBadge, StatusBadge } from '../components/ui'

export default function Home() {
  const s = useApp()
  const me = s.people.find((p) => p.id === s.currentUserId)!
  const myAssignments = s.assignments.filter((a) => a.personId === me.id && a.status === 'ACTIVE')
  const myProjects = myAssignments
    .map((a) => ({ a, p: s.projects.find((x) => x.id === a.projectId)! }))
    .filter(({ p }) => p && !p.deleted && ['진행중', '수주확정', '계약대기'].includes(p.status))
    .sort((x, y) => (x.p.status === '진행중' ? -1 : 1) - (y.p.status === '진행중' ? -1 : 1))
  const [util] = utilizationFor(CURRENT_MONTH, [me], s.projects, s.assignments)
  const notifs = s.notifications.filter((n) => n.recipientId === me.id).slice(0, 5)
  const myIssues = s.issues.filter((i) => i.assigneeId === me.id && i.status !== '완료')

  return (
    <>
      <h1 className="page">홈</h1>
      <p className="page-desc">{me.name}님, {CURRENT_MONTH.replace('-', '년 ')}월 기준 현황입니다.</p>
      <div className="grid cols-3">
        <div className="card">
          <h3>이번 달 내 가동률</h3>
          <div className="kpi">
            {Math.round(util.basic)}%<small>기본 (Σ배정 {util.totalMM.toFixed(1)}MM ÷ 가용 1.0)</small>
          </div>
          <div className="kpi" style={{ marginTop: 10 }}>
            <span className={util.adjusted > 100 ? 'util-over' : ''}>{Math.round(util.adjusted)}%</span>
            <small>보정 (직급계수 {me.gradeCoeff}) {util.adjusted > 100 && ' — 과부하'}</small>
          </div>
        </div>
        <div className="card">
          <h3>내 담당 열린 이슈 (유지보수)</h3>
          {myIssues.length === 0 ? <Empty>담당 이슈가 없습니다.</Empty> : (
            myIssues.slice(0, 4).map((i) => {
              const site = s.sites.find((x) => x.id === i.siteId)
              return (
                <div key={i.id} style={{ padding: '7px 0', borderBottom: '1px solid #f0f1f4', fontSize: 13 }}>
                  <Link to="/maintenance/issues">[{site?.customer}] {i.title}</Link>
                </div>
              )
            })
          )}
        </div>
        <div className="card">
          <h3>최근 알림</h3>
          {notifs.length === 0 ? <Empty>알림이 없습니다.</Empty> : (
            notifs.map((n) => (
              <div key={n.id} style={{ padding: '7px 0', borderBottom: '1px solid #f0f1f4', fontSize: 12.5, fontWeight: n.read ? 400 : 600 }}>
                {n.message}
              </div>
            ))
          )}
        </div>
      </div>
      <div className="card">
        <h3>내 프로젝트 ({myProjects.length})</h3>
        {myProjects.length === 0 ? <Empty>배정된 프로젝트가 없습니다.</Empty> : (
          <table>
            <thead>
              <tr><th>프로젝트</th><th>고객사</th><th>역할</th><th>상태</th><th>진행률</th><th>기간</th></tr>
            </thead>
            <tbody>
              {myProjects.map(({ a, p }) => (
                <tr key={p.id}>
                  <td><Link to={`/projects/${p.id}`}>{p.name}</Link></td>
                  <td>{p.client}</td>
                  <td><RoleBadge role={a.role} /></td>
                  <td><StatusBadge status={p.status} /></td>
                  <td><ProgressBar value={p.progress} /></td>
                  <td style={{ fontSize: 12, color: 'var(--muted)' }}>{p.startDate} ~ {p.endDate}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  )
}
