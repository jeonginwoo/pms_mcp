import { useStore } from '../store.jsx'
import { StatusBadge, Bar, TeamBars } from '../components/bits.jsx'
import { THIS_YM, ymLabel } from '../constants.js'

export default function Home() {
  const st = useStore()
  const { S, go, openProject, user, role, visibleProjects, scopePeople, utilOf, dday, MAINT } = st
  const u = user(), r = role()
  const month = THIS_YM

  const utilAll = scopePeople().map(p => ({ p, ...utilOf(p.id, month) }))
  const overCnt = utilAll.filter(x => x.adj > 100).length
  const avgAdj = utilAll.length ? Math.round(utilAll.reduce((s, x) => s + x.adj, 0) / utilAll.length) : 0
  const active = visibleProjects().filter(p => p.status === '진행중')
  const soonCnt = active.filter(p => { const t = dday(p.end).text; return t.startsWith('D-') && parseInt(t.slice(2), 10) <= 60 }).length
  const monthLogs = Object.values(MAINT).flat().filter(l => l.at >= `${month}-01`).length
  const maintCnt = st.projects().filter(p => p.status === '유지보수중').length

  const today = new Date()
  const yoil = ['일', '월', '화', '수', '목', '금', '토'][today.getDay()]
  const todayLabel = `${today.getFullYear()}년 ${today.getMonth() + 1}월 ${today.getDate()}일 (${yoil})`
  const scopeLabel = r === 'lead' ? u.team : r === 'member' ? '내 참여 프로젝트' : '부서 전체'

  const kpis = [
    { label: '진행 중 프로젝트', value: String(active.length), sub: `60일 내 마감 ${soonCnt}건`, color: 'var(--text)', go: () => go('projects') },
    { label: '평균 보정 가동률', value: `${avgAdj}%`, sub: `${utilAll.length}명 기준 · ${ymLabel(month)}`, color: 'var(--primary)', go: () => go('util') },
    { label: '오버부킹', value: `${overCnt}명`, sub: '보정 100% 초과', color: overCnt ? 'var(--danger)' : 'var(--ok)', go: () => go('util') },
    { label: `${ymLabel(month).split(' ')[1]} 유지보수 처리`, value: `${monthLogs}건`, sub: `계약 ${maintCnt}건 운영 중`, color: 'var(--text)', go: () => go('maint') },
  ]

  return (
    <div>
      <div style={{ marginBottom: 18 }}>
        <div style={{ fontSize: 19, fontWeight: 800, letterSpacing: '-.3px' }}>{u.name} {u.title}님, 안녕하세요</div>
        <div className="muted" style={{ fontSize: 13, marginTop: 2 }}>{todayLabel} · {scopeLabel} 기준 현황입니다.</div>
      </div>

      <div style={{ display: 'flex', background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 12, marginBottom: 16, overflow: 'hidden' }}>
        {kpis.map((k, i) => (
          <button key={i} onClick={k.go} style={{ flex: 1, textAlign: 'left', background: 'none', border: 'none', borderRight: i < 3 ? '1px solid var(--border-soft)' : 'none', padding: '14px 20px' }}>
            <span className="muted" style={{ fontSize: 12, fontWeight: 600 }}>{k.label}</span>
            <span style={{ fontSize: 22, fontWeight: 800, letterSpacing: '-.5px', color: k.color, marginLeft: 10 }}>{k.value}</span>
            <div className="muted2" style={{ fontSize: 11 }}>{k.sub}</div>
          </button>
        ))}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(280px,1fr) minmax(0,2fr)', gap: 16, alignItems: 'start' }}>
        <section className="card" style={{ padding: '18px 20px' }}>
          <h2 style={{ marginBottom: 14, fontSize: 14.5 }}>팀별 보정 가동률</h2>
          <TeamBars month={month} />
        </section>
        <section className="card" style={{ padding: '18px 20px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
            <h2 style={{ fontSize: 14.5 }}>진행 중 프로젝트</h2>
            <button className="btn-link" onClick={() => go('projects')}>전체 보기 →</button>
          </div>
          {active.map(p => {
            const dd = dday(p.end)
            return (
              <button key={p.id} className="trow" onClick={() => openProject(p.id)}
                style={{ gridTemplateColumns: 'minmax(0,1.6fr) 78px minmax(70px,1fr) 44px' }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontWeight: 700, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{p.name}</div>
                  <div className="muted2" style={{ fontSize: 11.5 }}>{p.client} · {p.team}</div>
                </div>
                <StatusBadge status={p.status} />
                <Bar value={p.progress} height={16} />
                <span style={{ fontSize: 11.5, color: dd.color, fontWeight: 600, textAlign: 'right' }}>{dd.text}</span>
              </button>
            )
          })}
          {active.length === 0 && <div className="empty">진행 중 프로젝트가 없습니다.</div>}
        </section>
      </div>
    </div>
  )
}
