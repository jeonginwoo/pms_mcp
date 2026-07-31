import { useStore } from '../store.jsx'
import { TeamBars } from '../components/bits.jsx'
import { THIS_YM, NEXT_YM, ymLabel } from '../constants.js'

const GRID = 'minmax(0,1.3fr) 64px 64px minmax(80px,1fr)'

export default function Util() {
  const st = useStore()
  const { S, setState, role, user, scopePeople, utilOf, teams } = st
  const r = role(), u = user()
  const month = S.utilMonth

  const rows = scopePeople().map(p => ({ p, ...utilOf(p.id, month) }))
  const avg = rows.length ? Math.round(rows.reduce((s, x) => s + x.adj, 0) / rows.length) : 0
  const over = rows.filter(x => x.adj > 100).sort((a, b) => b.adj - a.adj)
  const scopeText = (r === 'lead' ? u.team : r === 'member' ? '본인' : (S.utilTeam === '전체' ? '부서 전체' : S.utilTeam)) + ' · ' + ymLabel(month)
  const leafTeams = teams().filter(t => scopePeople().some(p => p.team === t) || true)

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(420px, 1fr))', gap: 16, alignItems: 'start' }}>
      <section className="card">
        <div className="card-head">
          <h2>가동률 <span className="muted2" style={{ fontWeight: 500, fontSize: 12.5 }}>{scopeText} · 평균 {avg}%</span></h2>
          <div style={{ display: 'flex', gap: 8 }}>
            <select value={month} onChange={e => setState({ utilMonth: e.target.value })}>
              <option value={THIS_YM}>{ymLabel(THIS_YM)}</option>
              <option value={NEXT_YM}>{ymLabel(NEXT_YM)}</option>
            </select>
            {(r === 'head' || r === 'admin') && (
              <select value={S.utilTeam} onChange={e => setState({ utilTeam: e.target.value })}>
                <option value="전체">부서 전체</option>
                {leafTeams.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            )}
          </div>
        </div>
        <div className="thead" style={{ gridTemplateColumns: GRID }}>
          <span>이름</span><span style={{ textAlign: 'right' }}>배정</span><span style={{ textAlign: 'right' }}>가동률</span><span>보정 (직급계수)</span>
        </div>
        {rows.map(x => (
          <div key={x.p.id} className="trow" style={{ gridTemplateColumns: GRID, padding: '9px 2px' }}>
            <span style={{ minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              <strong>{x.p.name}</strong> <span className="muted2" style={{ fontSize: 11.5 }}>{x.p.team} · {x.p.grade}</span>
            </span>
            <span className="muted" style={{ textAlign: 'right', fontSize: 12 }}>{x.mm} MM</span>
            <span style={{ textAlign: 'right' }}>{x.rate}%</span>
            <div className="bar">
              <div className={`bar-fill ${x.adj > 100 ? 'over' : ''}`} style={{ width: `${Math.min(x.adj, 150) / 150 * 100}%` }} />
              <span className="bar-label">{x.adj}%</span>
            </div>
          </div>
        ))}
        {rows.length === 0 && <div className="empty">조회 범위 내 인원이 없습니다.</div>}
      </section>

      <div style={{ display: 'grid', gap: 16 }}>
        <section className="card" style={{ padding: '18px 20px' }}>
          <h2 style={{ marginBottom: 14, fontSize: 14.5 }}>팀별 평균 (보정)</h2>
          <TeamBars month={month} />
          <div className="muted2" style={{ marginTop: 14, paddingTop: 12, borderTop: '1px solid var(--border-soft)', fontSize: 11.5 }}>
            보정 가동률 = 가동률 × 직급계수 ({st.S.grades.map(g => `${g.name} ${g.factor}`).join(' · ')})
          </div>
        </section>

        <section className="card" style={{ padding: '18px 20px' }}>
          <h2 style={{ fontSize: 14.5 }}>오버부킹 <span className="muted2" style={{ fontWeight: 500, fontSize: 12 }}>보정 100% 초과</span></h2>
          {over.length > 0 ? (
            <div style={{ display: 'grid', gap: 10, marginTop: 10 }}>
              {over.map(o => (
                <div key={o.p.id} style={{ background: 'var(--danger-soft)', border: '1px solid var(--danger-border)', borderLeft: '3px solid var(--danger)', borderRadius: 10, padding: '12px 14px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                    <span style={{ fontWeight: 700, fontSize: 13.5 }}>{o.p.name} <span className="muted2" style={{ fontWeight: 500, fontSize: 12 }}>· {o.p.team}</span></span>
                    <span style={{ background: 'rgba(216,58,58,.12)', color: 'var(--danger)', fontWeight: 800, fontSize: 12.5, padding: '2px 10px', borderRadius: 12 }}>{o.adj}%</span>
                  </div>
                  <div style={{ display: 'grid', gap: 4 }}>
                    {o.causes.map((c, i) => (
                      <div key={i} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12.5, borderTop: '1px dashed var(--danger-border)', paddingTop: 4 }}>
                        <span style={{ minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{c.pname}</span>
                        <span className="muted2" style={{ flex: 'none' }}>{c.mm} M/M</span>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="muted2" style={{ marginTop: 10, fontSize: 13 }}>해당 월 오버부킹 인원이 없습니다.</div>
          )}
        </section>
      </div>
    </div>
  )
}
