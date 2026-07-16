import { useStore } from '../store.jsx'
import { LogRow } from '../components/bits.jsx'

const TYPES = ['전체', 'SR', '장애', '정기점검', '패치']

export default function Maint() {
  const st = useStore()
  const { S, setState, projects, MAINT } = st

  const contracts = projects().filter(p => p.status === '유지보수중')
  const all = MAINT[S.maintId] || []
  const logs = all.filter(l => S.maintType === '전체' || l.type === S.maintType)

  if (contracts.length === 0) {
    return (
      <section className="card">
        <h2 style={{ marginBottom: 4 }}>유지보수 이력</h2>
        <div className="empty">유지보수중 상태의 계약이 없습니다. 프로젝트 상태를 ‘유지보수중’으로 변경하면 이 화면에서 이력을 관리할 수 있습니다.</div>
      </section>
    )
  }

  return (
    <section className="card">
      <div className="card-head">
        <h2>유지보수 이력 <span className="muted2" style={{ fontWeight: 500, fontSize: 12.5 }}>{logs.length}건 · 최근 50건</span></h2>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <select value={String(S.maintId)} onChange={e => setState({ maintId: Number(e.target.value) })} style={{ maxWidth: 320 }}>
            {contracts.map(p => <option key={p.id} value={p.id}>{p.name} ({p.client})</option>)}
          </select>
          <div style={{ display: 'flex', gap: 4 }}>
            {TYPES.map(t => (
              <button key={t} className={`chip-btn ${S.maintType === t ? 'on' : ''}`} onClick={() => setState({ maintType: t })}>{t}</button>
            ))}
          </div>
        </div>
      </div>
      <div style={{ display: 'grid' }}>
        {logs.map(l => <LogRow key={l.id} log={l} />)}
        {logs.length === 0 && <div className="empty">해당 유형의 이력이 없습니다.</div>}
      </div>
    </section>
  )
}
