import { useStore } from '../store.jsx'

/** 상태 배지 */
export function StatusBadge({ status, big }) {
  const { stColors } = useStore()
  const [c, bg] = stColors(status)
  return <span className="badge" style={{ color: c, background: bg, fontSize: big ? 12 : 11.5, padding: big ? '4px 12px' : undefined }}>{status}</span>
}

/** 진행률 바 (라벨 포함) */
export function Bar({ value, over, done, height = 17 }) {
  return (
    <div className="bar" style={{ height }}>
      <div className={`bar-fill ${over ? 'over' : ''} ${done ? 'done' : ''}`} style={{ width: `${Math.min(value, 100)}%` }} />
      <span className="bar-label" style={{ lineHeight: `${height}px` }}>{value}%</span>
    </div>
  )
}

/** 팀별 평균(보정) 바 목록 */
export function TeamBars({ month }) {
  const { people, teams, utilOf, rateColor } = useStore()
  const P = people()
  const rows = teams().map(t => {
    const members = P.filter(p => p.team === t)
    if (members.length === 0) return null
    const list = members.map(p => utilOf(p.id, month))
    const avg = Math.round(list.reduce((s, x) => s + x.adj, 0) / list.length)
    return { team: t, avg, barW: Math.min(avg, 140) / 140 * 100, color: rateColor(avg) }
  }).filter(Boolean)
  return (
    <div style={{ display: 'grid', gap: 12 }}>
      {rows.map(t => (
        <div key={t.team}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12.5, marginBottom: 4 }}>
            <span style={{ fontWeight: 600 }}>{t.team}</span>
            <span style={{ color: t.color, fontWeight: 700 }}>{t.avg}%</span>
          </div>
          <div className="minibar"><div style={{ width: `${t.barW}%`, background: t.color }} /></div>
        </div>
      ))}
    </div>
  )
}

/** 유지보수 로그 아이콘+행 */
export function LogRow({ log, compact }) {
  const { mkLog } = useStore()
  const l = mkLog(log)
  return (
    <div style={{ display: 'flex', gap: compact ? 12 : 14, padding: compact ? '10px 0' : '13px 2px', borderBottom: '1px solid var(--border-soft)' }}>
      <span style={{ flex: 'none', width: compact ? 26 : 30, height: compact ? 26 : 30, borderRadius: 9, background: l.iconBg, color: l.iconColor, display: 'grid', placeItems: 'center', fontSize: 10.5, fontWeight: 800 }}>{l.typeShort}</span>
      <div style={{ minWidth: 0, flex: 1 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <strong style={{ fontSize: compact ? 13 : 13.5 }}>{l.title}</strong>
          {!compact && <span className="badge" style={{ background: 'var(--chip)', border: '1px solid var(--border)', color: 'var(--muted)', fontSize: 11, padding: '0 8px' }}>{l.type}</span>}
          <span className="muted2" style={{ fontSize: 11.5 }}>{l.at}</span>
        </div>
        <div className="muted" style={{ fontSize: 12.5, marginTop: 2 }}>{l.note}</div>
      </div>
    </div>
  )
}
