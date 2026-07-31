import { useEffect, useRef } from 'react'
import { useStore } from '../store.jsx'
import ThemeToggle from './ThemeToggle.jsx'

const TITLES = { home: '홈', projects: '프로젝트', util: '가동률 · 오버부킹', maint: '유지보수 이력', people: '인력 · 조직', settings: '설정 · 관리' }

export default function Header({ theme, onToggleTheme }) {
  const { S, setState, notifList, markAllNotifs } = useStore()
  const bellRef = useRef(null)
  const notifs = notifList()
  const unread = notifs.filter(n => n.unread).length

  useEffect(() => {
    const h = (e) => { if (bellRef.current && !bellRef.current.contains(e.target)) setState({ notifOpen: false }) }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [setState])

  return (
    <header className="topbar">
      <div className="page-title">{TITLES[S.route] || ''}</div>

      <button
        className="btn"
        onClick={() => setState(s => ({ chatOpen: !s.chatOpen }))}
        style={{
          display: 'flex', alignItems: 'center', gap: 8, padding: '8px 14px', borderRadius: 9,
          border: `1px solid ${S.chatOpen ? 'var(--primary)' : 'var(--border)'}`,
          background: S.chatOpen ? 'var(--primary)' : 'var(--surface)',
          color: S.chatOpen ? '#fff' : 'var(--primary)',
        }}
      >
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M21 11.5a8.38 8.38 0 01-8.5 8.5 8.6 8.6 0 01-3.9-.9L3 21l1.9-5.6a8.38 8.38 0 01-.9-3.9A8.5 8.5 0 1121 11.5z" /></svg>
        AI 어시스턴트
      </button>

      <div className="bell-wrap" ref={bellRef}>
        <button className="icon-btn" onClick={() => setState(s => ({ notifOpen: !s.notifOpen }))} aria-label="알림">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 01-3.4 0" /></svg>
          {unread > 0 && <span className="bell-cnt">{unread}</span>}
        </button>
        {S.notifOpen && (
          <div className="bell-panel">
            <div className="bell-head">
              <strong style={{ fontSize: 13.5 }}>알림</strong>
              <button className="btn-link" onClick={markAllNotifs}>모두 읽음</button>
            </div>
            <div style={{ maxHeight: 380, overflowY: 'auto' }}>
              {notifs.map(n => (
                <div key={n.id} className={`bell-item ${n.unread ? 'unread' : ''}`}>
                  <span className="bell-dot" style={{ opacity: n.unread ? 1 : .15 }} />
                  <div>
                    <div style={{ fontSize: 12.5, lineHeight: 1.45 }}>{n.msg}</div>
                    <div className="muted2" style={{ fontSize: 11, marginTop: 2 }}>{n.at}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      <ThemeToggle theme={theme} onToggle={onToggleTheme} />
    </header>
  )
}
