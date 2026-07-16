import { useStore } from '../store.jsx'
import { HQ_TEAM } from '../constants.js'

export default function People() {
  const st = useStore()
  const { S, setState, people, person, teams, role, user, utilOf, rateColor, proj, ASSIGNMENTS } = st
  const r = role(), u = user()
  const month = S.utilMonth
  const P = people()

  const scope = r === 'lead' ? P.filter(p => p.team === u.team) : P.filter(p => p.team !== HQ_TEAM)
  const groups = teams().filter(t => scope.some(p => p.team === t)).map(t => {
    const members = scope.filter(p => p.team === t)
    const rows = members.map(p => ({ p, ...utilOf(p.id, month) }))
    const avg = rows.length ? Math.round(rows.reduce((s, x) => s + x.adj, 0) / rows.length) : 0
    return { team: t, avg, rows }
  })

  const psel = S.personSel ? person(S.personSel) : null
  const pu = psel ? utilOf(psel.id, month) : null
  const pAssign = psel ? ASSIGNMENTS.filter(a => a.personId === psel.id).map(a => ({ pname: (proj(a.projectId) || {}).name || '', month: `${Number(a.month.slice(5))}월`, ym: a.month, mm: a.mm })) : []
  const gradeFactor = Object.fromEntries(S.grades.map(g => [g.name, g.factor]))

  return (
    <div style={{ display: 'grid', gridTemplateColumns: psel ? 'minmax(0,1fr) 340px' : 'minmax(0,1fr)', gap: 16, alignItems: 'start' }}>
      <div style={{ display: 'grid', gap: 16 }}>
        {groups.map(g => (
          <section key={g.team} className="card" style={{ padding: '18px 20px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 12 }}>
              <h2 style={{ fontSize: 14.5 }}>{g.team} <span className="muted2" style={{ fontWeight: 500, fontSize: 12 }}>{g.rows.length}명</span></h2>
              <span className="muted" style={{ fontSize: 12 }}>팀 평균 보정 <strong style={{ color: rateColor(g.avg) }}>{g.avg}%</strong></span>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(210px, 1fr))', gap: 10 }}>
              {g.rows.map(x => {
                const projNames = ASSIGNMENTS.filter(a => a.personId === x.p.id && a.month === month).map(a => (proj(a.projectId) || {}).name || '')
                return (
                  <button key={x.p.id} className={`person-card ${S.personSel === x.p.id ? 'sel' : ''}`}
                    onClick={() => setState({ personSel: S.personSel === x.p.id ? null : x.p.id })}>
                    <span className="avatar" style={{ flex: 'none', width: 34, height: 34, fontSize: 13 }}>{x.p.name[0]}</span>
                    <span style={{ minWidth: 0, flex: 1 }}>
                      <span style={{ display: 'block', fontWeight: 700, fontSize: 13, color: 'var(--text)' }}>
                        {x.p.name} <span className="muted2" style={{ fontWeight: 500, fontSize: 11.5 }}>{x.p.title}</span>
                      </span>
                      <span className="muted2" style={{ display: 'block', fontSize: 11.5, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {projNames.length ? projNames.map(n => n.split(' ')[0]).join(' · ') : '배정 없음'}
                      </span>
                    </span>
                    <span style={{ flex: 'none', fontSize: 12, fontWeight: 800, color: rateColor(x.adj) }}>{x.adj}%</span>
                  </button>
                )
              })}
            </div>
          </section>
        ))}
      </div>

      {psel && (
        <aside className="card" style={{ position: 'sticky', top: 0 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 14 }}>
            <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
              <span className="avatar" style={{ width: 44, height: 44, fontSize: 16, fontWeight: 800 }}>{psel.name[0]}</span>
              <div>
                <div style={{ fontWeight: 800, fontSize: 15 }}>{psel.name} <span className="muted" style={{ fontWeight: 500, fontSize: 12.5 }}>{psel.title}</span></div>
                <div className="muted2" style={{ fontSize: 12 }}>{psel.team} · 직급계수 {gradeFactor[psel.grade] ?? 1}</div>
              </div>
            </div>
            <button className="btn btn-ghost" style={{ width: 28, height: 28, padding: 0 }} onClick={() => setState({ personSel: null })}>✕</button>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 16 }}>
            <div className="metric-box"><div className="k">이번 달 가동률</div><div style={{ fontSize: 19, fontWeight: 800 }}>{pu.rate}%</div></div>
            <div className="metric-box"><div className="k">보정 가동률</div><div style={{ fontSize: 19, fontWeight: 800, color: rateColor(pu.adj) }}>{pu.adj}%</div></div>
          </div>
          <h3 style={{ marginBottom: 8, fontSize: 13 }}>배정 내역</h3>
          <div style={{ display: 'grid', gap: 2, maxHeight: 320, overflowY: 'auto' }}>
            {pAssign.map((a, i) => (
              <div key={i} style={{ display: 'flex', justifyContent: 'space-between', gap: 10, padding: '8px 0', borderBottom: '1px solid var(--border-soft)', fontSize: 12.5 }}>
                <span style={{ minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{a.pname}</span>
                <span className="muted2" style={{ flex: 'none' }}>{a.ym} · {a.mm} M/M</span>
              </div>
            ))}
            {pAssign.length === 0 && <div className="muted2" style={{ fontSize: 12.5, padding: '8px 0' }}>배정 내역이 없습니다. 투입 가능한 인력입니다.</div>}
          </div>
        </aside>
      )}
    </div>
  )
}
