/*
 * 좌측 내비 — 백엔드에 있는 화면만 싣는다.
 * 가동률·유지보수·알림·설정(사용자 관리)은 해당 모듈이 아직 없어 항목을 두지 않는다
 * (없는 화면을 목업으로 채우지 않는다).
 */
import { useStore } from '../store'
import type { Route } from '../store'

const ICONS: Record<Route, string> = {
  home: 'M3 11l9-8 9 8v9a1 1 0 01-1 1h-5v-6h-6v6H4a1 1 0 01-1-1z',
  projects: 'M3 7a2 2 0 012-2h4l2 2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z',
  people: 'M17 21v-2a4 4 0 00-4-4H7a4 4 0 00-4 4v2M14 7a4 4 0 11-8 0 4 4 0 018 0M21 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75',
}

const NAV: { key: Route; label: string }[] = [
  { key: 'home', label: '홈' },
  { key: 'projects', label: '프로젝트' },
  { key: 'people', label: '인력 · 조직' },
]

export default function Sidebar() {
  const { route, go, me, logout, sessionMode } = useStore()

  return (
    <aside className="sidebar">
      <div className="side-logo">
        <div className="mark">P</div>
        <div>
          <div style={{ fontWeight: 800, fontSize: 15, letterSpacing: '-.2px' }}>프로텐 PMS</div>
          <div className="muted" style={{ fontSize: 11 }}>프로젝트 관리 시스템</div>
        </div>
      </div>

      <nav className="side-nav">
        {NAV.map((item) => (
          <button
            key={item.key}
            className={`nav-btn ${route === item.key ? 'active' : ''}`}
            onClick={() => go(item.key)}
          >
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
              strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"
              style={{ flex: 'none' }}>
              <path d={ICONS[item.key]} />
            </svg>
            <span>{item.label}</span>
          </button>
        ))}
      </nav>

      <div className="side-user">
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div className="avatar" style={{ width: 34, height: 34, fontSize: 13, flex: 'none' }}>
            {me?.name.slice(0, 1) ?? '·'}
          </div>
          <div style={{ minWidth: 0, flex: 1 }}>
            <div style={{ fontWeight: 700, fontSize: 13 }}>
              {me?.name ?? '—'}
              <span className="muted" style={{ fontWeight: 500 }}> {me?.grade ?? ''}</span>
            </div>
            <div className="muted" style={{ fontSize: 11.5 }}>{me?.orgUnit ?? ''}</div>
          </div>
        </div>
        <button className="profile-item danger" onClick={logout}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
            strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" style={{ flex: 'none' }}>
            <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9" />
          </svg>
          {sessionMode === 'caller' ? '화자 해제' : '로그아웃'}
        </button>
      </div>
    </aside>
  )
}
