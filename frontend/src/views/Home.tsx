/*
 * 홈 — 지금 붙어 있는 데이터(가시성 범위의 프로젝트·인력)만으로 만든 요약.
 * 가동률·알림·마감 임박은 각 모듈(resource·notification)이 생기면 들어온다.
 */
import { useStore } from '../store'
import { STATUS_LABEL, STATUS_ORDER } from '../labels'
import { Bar, Empty, Metric, StatusBadge } from '../components/ui'

export default function Home() {
  const { me, people, projects, openProject, go } = useStore()

  const counts = STATUS_ORDER.map((status) => ({
    status,
    count: projects.filter((project) => project.status === status).length,
  }))
  const mine = projects.filter((project) => project.managerId === me?.id)
  const recent = projects.slice(0, 6)

  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <section className="card">
        <div className="card-head">
          <h2>{me?.name ?? ''}님, 안녕하세요</h2>
          <span className="muted2" style={{ fontSize: 12 }}>
            {me?.orgUnit} · {me?.grade}
          </span>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 10 }}>
          <Metric label="가시성 범위 프로젝트" value={`${projects.length}건`} />
          <Metric label="내가 PM인 프로젝트" value={`${mine.length}건`} />
          <Metric label="가시성 범위 인력" value={`${people.length}명`} />
        </div>
      </section>

      <section className="card">
        <div className="card-head">
          <h2>상태 분포</h2>
          <button className="btn-link" onClick={() => go('projects')}>프로젝트 전체 보기 →</button>
        </div>
        <div style={{ display: 'grid', gap: 10 }}>
          {counts.map((row) => (
            <div key={row.status} style={{ display: 'grid', gridTemplateColumns: '96px 1fr 40px', gap: 10, alignItems: 'center' }}>
              <StatusBadge status={row.status} />
              <div className="minibar">
                <div style={{
                  width: projects.length === 0 ? '0%' : `${(row.count / projects.length) * 100}%`,
                  background: 'var(--primary)',
                }} />
              </div>
              <span style={{ fontSize: 12.5, fontWeight: 700, textAlign: 'right' }}>{row.count}</span>
            </div>
          ))}
        </div>
      </section>

      <section className="card">
        <div className="card-head">
          <h2>최근 프로젝트</h2>
        </div>
        {recent.map((project) => (
          <button key={project.id} className="trow"
            style={{ gridTemplateColumns: 'minmax(0,2fr) 110px 84px minmax(90px,1fr)', padding: '11px 2px' }}
            onClick={() => void openProject(project.id)}>
            <span style={{ fontWeight: 700, minWidth: 0, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {project.name}
            </span>
            <span className="muted">{project.client}</span>
            <StatusBadge status={project.status} />
            <Bar value={project.progress} done={project.status === 'COMPLETED'} />
          </button>
        ))}
        {recent.length === 0 && (
          <Empty>
            아직 프로젝트가 없습니다. {STATUS_LABEL.CONTRACT_PENDING} 상태로 새로 만들어 보세요.
          </Empty>
        )}
      </section>
    </div>
  )
}
