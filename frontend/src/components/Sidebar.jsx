import { useStore } from '../store.jsx'
import { THIS_YM } from '../constants.js'

const ICONS = {
  home: 'M3 11l9-8 9 8v9a1 1 0 01-1 1h-5v-6h-6v6H4a1 1 0 01-1-1z',
  projects: 'M3 7a2 2 0 012-2h4l2 2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z',
  util: 'M5 20V10M11 20V4M17 20v-6M2 20h20',
  maint: 'M14.7 6.3a4.5 4.5 0 00-6 5.9L3 18v3h3l5.8-5.7a4.5 4.5 0 005.9-6l-3 3-2.3-2.3z',
  people: 'M17 21v-2a4 4 0 00-4-4H7a4 4 0 00-4 4v2M14 7a4 4 0 11-8 0 4 4 0 018 0M21 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75',
  settings: 'M12 8a4 4 0 100 8 4 4 0 000-8M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M19.1 4.9L17 7M7 17l-2.1 2.1',
}

const PROFILE_MENU = [
  { label: '프로필 설정', tab: 'profile', icon: 'M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2M16 7a4 4 0 11-8 0 4 4 0 018 0' },
  { label: '비밀번호 변경', tab: 'password', icon: 'M19 11H5a2 2 0 00-2 2v7a2 2 0 002 2h14a2 2 0 002-2v-7a2 2 0 00-2-2zM7 11V7a5 5 0 0110 0v4' },
  { label: '알림 설정', tab: 'notif', icon: 'M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 01-3.4 0' },
]

export default function Sidebar() {
  const st = useStore()
  const { S, setState, go, logout, openAcct, scopePeople, utilOf, user } = st
  const u = user()
  const overCnt = scopePeople().map(p => utilOf(p.id, THIS_YM)).filter(x => x.adj > 100).length

  const NAV = [
    { key: 'home', label: '홈' },
    { key: 'projects', label: '프로젝트' },
    { key: 'util', label: '가동률 · 오버부킹', badge: overCnt > 0 ? String(overCnt) : null },
    { key: 'maint', label: '유지보수' },
    { key: 'people', label: '인력 · 조직' },
    { key: 'settings', label: '설정 · 관리' },
  ]

  return (
    <aside className="sidebar">
      <div className="side-logo">
        <div className="mark">P</div>
        <div>
          <div style={{ fontWeight: 800, fontSize: 15, letterSpacing: '-.2px' }}>{S.companyName} PMS</div>
          <div className="muted" style={{ fontSize: 11 }}>프로젝트 관리 시스템</div>
        </div>
      </div>
      <nav className="side-nav">
        {NAV.map(n => (
          <button key={n.key} className={`nav-btn ${S.route === n.key ? 'active' : ''}`} onClick={() => go(n.key)}>
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" style={{ flex: 'none' }}><path d={ICONS[n.key]} /></svg>
            <span>{n.label}</span>
            {n.badge && <span className="nav-badge">{n.badge}</span>}
          </button>
        ))}
      </nav>

      {/* 프로필 아코디언 — 클릭해 계정 메뉴 펼침 (프로토타입 사양) */}
      <div className="side-user" style={{ display: 'block' }}>
        <button onClick={() => setState(s => ({ profileOpen: !s.profileOpen }))}
          style={{ display: 'flex', alignItems: 'center', gap: 10, background: 'none', border: 'none', padding: 0, width: '100%', textAlign: 'left', color: 'inherit' }}>
          <div className="avatar" style={{ width: 34, height: 34, fontSize: 13, flex: 'none' }}>{u.name?.[0]}</div>
          <div style={{ minWidth: 0, flex: 1 }}>
            <div style={{ fontWeight: 700, fontSize: 13 }}>{u.name} <span className="muted" style={{ fontWeight: 500 }}>{u.title}</span></div>
            <div className="muted" style={{ fontSize: 11.5 }}>{u.team}</div>
          </div>
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="var(--muted2)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
            style={{ flex: 'none', transform: `rotate(${S.profileOpen ? 0 : 180}deg)`, transition: 'transform .18s' }}>
            <path d="M18 15l-6-6-6 6" />
          </svg>
        </button>
        <div style={{ display: 'grid', gridTemplateRows: S.profileOpen ? '1fr' : '0fr', transition: 'grid-template-rows .2s ease', marginTop: S.profileOpen ? 10 : 0 }}>
          <div style={{ overflow: 'hidden' }}>
            <div style={{ display: 'grid', gap: 1, paddingTop: 2 }}>
              {PROFILE_MENU.map(mi => (
                <button key={mi.tab} onClick={() => openAcct(mi.tab)}
                  className="profile-item">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" style={{ flex: 'none' }}><path d={mi.icon} /></svg>
                  {mi.label}
                </button>
              ))}
              <div style={{ height: 1, background: 'var(--border-soft)', margin: '4px 2px' }} />
              <button onClick={logout} className="profile-item danger">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" style={{ flex: 'none' }}><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9" /></svg>
                로그아웃
              </button>
            </div>
          </div>
        </div>
      </div>
    </aside>
  )
}
