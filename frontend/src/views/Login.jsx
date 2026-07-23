import { useState } from 'react'
import { useStore } from '../store.jsx'
import { DEMO_ACCOUNTS, DEMO_PASSWORD } from '../constants.js'
import HealthBadge from '../components/HealthBadge.tsx'

/** 로그인 — 이메일 + 비밀번호. 데모 계정 카드는 클릭 시 바로 로그인 (프로토타입 사양) */
export default function Login({ theme, onToggleTheme }) {
  const { S, loginSubmit } = useStore()
  const [email, setEmail] = useState('')
  const [pw, setPw] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (em, p) => {
    if (busy) return
    setBusy(true)
    try { await loginSubmit((em ?? email).trim().toLowerCase(), p ?? pw) }
    finally { setBusy(false) }
  }

  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', padding: 32, position: 'relative' }}>
      <div style={{ position: 'absolute', top: 20, right: 20 }}>
        <button className="theme-toggle btn-ghost btn" onClick={onToggleTheme} title="테마 전환">{theme === 'dark' ? '☀️' : '🌙'}</button>
      </div>
      <div style={{ width: 400, maxWidth: '100%' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, justifyContent: 'center', marginBottom: 26 }}>
          <div style={{ width: 40, height: 40, borderRadius: 10, background: 'var(--primary)', color: '#fff', display: 'grid', placeItems: 'center', fontWeight: 800, fontSize: 16 }}>P</div>
          <div>
            <div style={{ fontWeight: 800, fontSize: 19, letterSpacing: '-.2px' }}>프로텐 PMS</div>
            <div className="muted" style={{ fontSize: 12 }}>프로젝트 관리 시스템</div>
          </div>
        </div>

        <div className="card" style={{ borderRadius: 14, padding: 24 }}>
          <div style={{ display: 'grid', gap: 12 }}>
            <label style={{ display: 'grid', gap: 5 }}>
              <span className="muted" style={{ fontSize: 12, fontWeight: 600 }}>이메일</span>
              <input value={email} placeholder="name@proten.co.kr" autoFocus
                onChange={e => setEmail(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') submit() }}
                style={{ fontSize: 13.5, borderRadius: 9, padding: '10px 12px' }} />
            </label>
            <label style={{ display: 'grid', gap: 5 }}>
              <span className="muted" style={{ fontSize: 12, fontWeight: 600 }}>비밀번호</span>
              <input type="password" value={pw} placeholder="비밀번호"
                onChange={e => setPw(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') submit() }}
                style={{ fontSize: 13.5, borderRadius: 9, padding: '10px 12px' }} />
            </label>
            {S.loginErr && <div className="form-err">{S.loginErr}</div>}
            <button className="btn btn-primary" disabled={busy}
              style={{ borderRadius: 9, padding: 11, fontSize: 14, fontWeight: 700, marginTop: 2, opacity: busy ? .6 : 1 }}
              onClick={() => submit()}>
              {busy ? '로그인 중…' : '로그인'}
            </button>
          </div>
        </div>

        <HealthBadge intervalMs={10000} />

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, margin: '20px 0 12px' }}>
          <span style={{ flex: 1, height: 1, background: 'var(--border)' }} />
          <span className="muted2" style={{ fontSize: 11.5, fontWeight: 600 }}>데모 계정 — 클릭하면 바로 로그인</span>
          <span style={{ flex: 1, height: 1, background: 'var(--border)' }} />
        </div>
        <div style={{ display: 'grid', gap: 8 }}>
          {DEMO_ACCOUNTS.map(a => (
            <button key={a.email} disabled={busy}
              onClick={() => submit(a.email, DEMO_PASSWORD)}
              style={{ display: 'flex', alignItems: 'center', gap: 11, background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 11, padding: '10px 14px', textAlign: 'left', width: '100%' }}>
              <span style={{ width: 32, height: 32, borderRadius: '50%', background: a.badge[0], color: a.badge[1], display: 'grid', placeItems: 'center', fontWeight: 700, fontSize: 12.5, flex: 'none' }}>{a.name[0]}</span>
              <span style={{ flex: 1, minWidth: 0 }}>
                <span style={{ display: 'block', fontSize: 13, fontWeight: 700, color: 'var(--text)' }}>{a.name} <span className="muted" style={{ fontWeight: 500 }}>{a.roleLabel}</span></span>
                <span className="muted2" style={{ display: 'block', fontSize: 11.5 }}>{a.email} · 비밀번호 {DEMO_PASSWORD}</span>
              </span>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--muted2)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flex: 'none' }}><path d="M9 18l6-6-6-6" /></svg>
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
