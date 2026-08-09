// 공통 레이아웃 — 사이드바 + 헤더(알림 뱃지·사용자 전환·내 계정)
import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { loginAs, logout, markAllRead, markRead, updateNotifPrefs, updateProfile, useApp } from '../core/store'
import { groupOf, orgCanCreateProject, orgIsAdmin } from '../core/permissions'
import { Field, Modal } from './ui'
import ChatPanel from './ChatPanel'

export default function Layout() {
  const s = useApp()
  const nav = useNavigate()
  const [showNotif, setShowNotif] = useState(false)
  const [showAccount, setShowAccount] = useState(false)
  const [showChat, setShowChat] = useState(false)
  const user = s.people.find((p) => p.id === s.currentUserId)!
  const myNotifs = s.notifications.filter((n) => n.recipientId === user.id)
  const unread = myNotifs.filter((n) => !n.read).length

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="logo">
          PROTEN PMS
          <small>화면 프로토타입 — 기획 검증용</small>
        </div>
        <nav>
          <NavLink to="/" end>홈</NavLink>
          <NavLink to="/projects">프로젝트</NavLink>
          <NavLink to="/utilization">가동률</NavLink>
          <NavLink to="/maintenance" end>유지보수 계약</NavLink>
          <NavLink to="/maintenance/issues">유지보수 이슈</NavLink>
          <NavLink to="/people">인력</NavLink>
          {orgIsAdmin(user, s.roleGroups) && <NavLink to="/settings">설정</NavLink>}
        </nav>
        <div className="proto-note">
          목업 데이터로 동작합니다 (시드 44명·382건).
          권한·가시성·오류 응답은 PRD 규칙을 재현하며, 새로고침 시 초기화됩니다.
        </div>
      </aside>
      <div className="main">
        <header className="topbar">
          <span style={{ fontWeight: 600 }}>{user.name}</span>
          <span className="user">{user.division} · {user.team}{user.isSystem ? '' : ` · ${user.grade}`} · {groupOf(user, s.roleGroups).name}</span>
          <span className="spacer" />
          <label className="switcher">
            사용자 전환(검증용):{' '}
            <select
              value={user.id}
              onChange={(e) => { setShowNotif(false); loginAs(Number(e.target.value)) }}
            >
              {s.people.filter((p) => p.active).map((p) => (
                <option key={p.id} value={p.id}>{p.name} — {p.team} ({groupOf(p, s.roleGroups).name})</option>
              ))}
            </select>
          </label>
          <button className="bell" title="알림" onClick={() => setShowNotif(!showNotif)}>
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M6 9a6 6 0 1 1 12 0c0 5 2 6 2 6H4s2-1 2-6" /><path d="M10 19a2 2 0 0 0 4 0" /></svg>
            {unread > 0 && <span className="dot">{unread}</span>}
          </button>
          <button className="btn sm" onClick={() => setShowAccount(true)}>내 계정</button>
          <button className="btn sm" onClick={() => { logout(); nav('/login') }}>로그아웃</button>
        </header>
        {showNotif && (
          <div className="notif-drop">
            <div style={{ padding: '10px 14px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--border)' }}>
              <b style={{ fontSize: 13 }}>알림</b>
              <button className="btn sm" onClick={markAllRead}>모두 읽음</button>
            </div>
            {myNotifs.length === 0 && <div className="empty">알림이 없습니다.</div>}
            {myNotifs.map((n) => (
              <div key={n.id} className={`notif-item ${n.read ? '' : 'unread'}`} onClick={() => markRead(n.id)}>
                {n.message}
                <div className="meta">{n.createdAt}{!n.read && ' · 읽지 않음'}</div>
              </div>
            ))}
          </div>
        )}
        <main className="content" onClick={() => showNotif && setShowNotif(false)}>
          <Outlet />
        </main>
      </div>
      {showChat && <ChatPanel onClose={() => setShowChat(false)} />}
      {!showChat && (
        <button className="chat-fab" title="AI 어시스턴트" onClick={() => setShowChat(true)}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 12a8 8 0 0 1-8 8H4l2-3a8 8 0 1 1 15-5z" /></svg>
          AI 어시스턴트
        </button>
      )}
      {showAccount && <AccountModal onClose={() => setShowAccount(false)} />}
    </div>
  )
}

function AccountModal({ onClose }: { onClose: () => void }) {
  const s = useApp()
  const user = s.people.find((p) => p.id === s.currentUserId)!
  const [name, setName] = useState(user.name)
  const [email, setEmail] = useState(user.email)
  const [phone, setPhone] = useState(user.phone ?? '')
  const [prefs, setPrefs] = useState(user.notifPrefs)
  const [msg, setMsg] = useState('')

  const save = () => {
    const r = updateProfile(name, email, phone)
    if (!r.ok) { setMsg(`${r.code}: ${r.message}`); return }
    updateNotifPrefs(prefs)
    onClose()
  }
  return (
    <Modal title="내 계정" onClose={onClose}>
      <Field label="이름"><input value={name} onChange={(e) => setName(e.target.value)} /></Field>
      <Field label="이메일 (로그인 ID)"><input value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
      <Field label="연락처"><input value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="010-0000-0000" /></Field>
      <Field label="비밀번호 변경">
        <input type="password" placeholder="프로토타입에서는 데모만 — 8자 이상 규칙(H1-3)" disabled />
      </Field>
      <Field label="알림 설정 (H1-4 — 끄면 해당 유형 알림이 적재되지 않음)">
        <div style={{ display: 'flex', gap: 14, fontSize: 13 }}>
          {(['progress', 'project', 'org', 'weekly'] as const).map((k) => (
            <label key={k}>
              <input type="checkbox" checked={prefs[k]} onChange={(e) => setPrefs({ ...prefs, [k]: e.target.checked })} />{' '}
              {{ progress: '진척', project: '프로젝트', org: '조직', weekly: '주간' }[k]}
            </label>
          ))}
        </div>
      </Field>
      {msg && <div className="error-box">{msg}</div>}
      <div className="actions">
        <button className="btn" onClick={onClose}>닫기</button>
        <button className="btn primary" onClick={save}>저장</button>
      </div>
    </Modal>
  )
}

export function useCurrentUser() {
  const s = useApp()
  return s.people.find((p) => p.id === s.currentUserId) ?? null
}

export { orgCanCreateProject }
