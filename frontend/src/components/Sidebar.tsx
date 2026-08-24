/*
 * 좌측 내비 — 백엔드에 있는 화면만 싣는다(없는 화면을 목업으로 채우지 않는다).
 *
 * 2026-08-24: 가동률(EPIC C)·유지보수 조회(EPIC D 조회분)가 들어왔다. 알림은 백엔드가
 * 아직 501 골격이라 항목이 없다.
 */
import { useStore } from '../store'
import type { Route } from '../store'

const ICONS: Record<Route, string> = {
  home: 'M3 11l9-8 9 8v9a1 1 0 01-1 1h-5v-6h-6v6H4a1 1 0 01-1-1z',
  projects: 'M3 7a2 2 0 012-2h4l2 2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z',
  people: 'M17 21v-2a4 4 0 00-4-4H7a4 4 0 00-4 4v2M14 7a4 4 0 11-8 0 4 4 0 018 0M21 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75',
  utilization: 'M3 3v18h18M7 15l3-4 3 3 4-6',
  maintenance: 'M14.7 6.3a4 4 0 01-5 5L4 17v3h3l5.7-5.7a4 4 0 015-5l1.6-1.6-3-3z',
  issues: 'M12 9v4m0 4h.01M10.3 3.9L2 18a1.7 1.7 0 001.5 2.5h17A1.7 1.7 0 0022 18L13.7 3.9a1.7 1.7 0 00-3 0z',
  audit: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2M9 12h6M9 16h4',
}

/** 관리 플래그가 있을 때만 보이는 항목 — 숨기는 것은 표시용이고 판정은 서버다 */
const MANAGED: Route[] = ['audit']

const NAV: { key: Route; label: string }[] = [
  { key: 'home', label: '홈' },
  { key: 'projects', label: '프로젝트' },
  { key: 'people', label: '인력 · 조직' },
  { key: 'utilization', label: '가동률' },
  { key: 'maintenance', label: '유지보수' },
  { key: 'issues', label: '유지보수 이슈' },
  { key: 'audit', label: '감사 로그' },
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
        {NAV.filter((item) => !MANAGED.includes(item.key) || me?.manageOrg).map((item) => (
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
