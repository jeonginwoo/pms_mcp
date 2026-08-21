/*
 * 로그인 — 이메일 + 비밀번호 (`POST /api/auth/login`은 인증 스위치와 무관하게 열려 있다).
 * 데모 계정은 실 조직 시드 계정이다: 초기 비밀번호는 전원 공용이며 개발·데모용이다.
 */
import { useState } from 'react'
import { useStore } from '../store'
import type { Theme } from '../theme'

const DEMO_PASSWORD = 'proten1!'

/**
 * 권한 그룹이 다른 4명 — 가시성 차이를 눌러 보며 확인할 수 있게 고른 시드 계정.
 * personId는 화자 지정(로그인 우회) 경로가 헤더에 실을 값이다.
 */
const DEMO_ACCOUNTS = [
  { personId: 1, name: '박재완', role: '관리자 · 전사', email: 'pro0001@proten.co.kr', accent: ['rgba(194,97,30,.13)', '#c2611e'] },
  { personId: 5, name: '주정호', role: '부문장 · AX사업기획부', email: '20260001@proten.co.kr', accent: ['rgba(107,91,210,.14)', '#6b5bd2'] },
  { personId: 17, name: '이현창', role: '팀장 · AX솔루션개발1팀', email: 'pro0016@proten.co.kr', accent: ['rgba(61,99,216,.13)', '#3d63d8'] },
  { personId: 18, name: '김경민', role: '팀원 · AX솔루션개발1팀', email: 'pro0017@proten.co.kr', accent: ['rgba(31,138,76,.13)', '#1f8a4c'] },
]

interface Props {
  theme: Theme
  onToggleTheme: () => void
}

export default function Login({ theme, onToggleTheme }: Props) {
  const { loginError, submitLogin, enterAsCaller } = useStore()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [callerId, setCallerId] = useState('1')
  const [busy, setBusy] = useState(false)

  const enterWithoutLogin = async (personId: number) => {
    if (busy || !Number.isFinite(personId) || personId <= 0) {
      return
    }

    setBusy(true)

    try {
      await enterAsCaller(personId)
    } finally {
      setBusy(false)
    }
  }

  const submit = async (accountEmail?: string, accountPassword?: string) => {
    if (busy) {
      return
    }

    setBusy(true)

    try {
      await submitLogin(
        (accountEmail ?? email).trim().toLowerCase(),
        accountPassword ?? password)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', padding: 32, position: 'relative' }}>
      <div style={{ position: 'absolute', top: 20, right: 20 }}>
        <button className="theme-toggle btn btn-ghost" onClick={onToggleTheme} title="테마 전환">
          {theme === 'dark' ? '☀️' : '🌙'}
        </button>
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
                onChange={(e) => setEmail(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') { void submit() } }}
                style={{ fontSize: 13.5, borderRadius: 9, padding: '10px 12px' }} />
            </label>
            <label style={{ display: 'grid', gap: 5 }}>
              <span className="muted" style={{ fontSize: 12, fontWeight: 600 }}>비밀번호</span>
              <input type="password" value={password} placeholder="비밀번호"
                onChange={(e) => setPassword(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') { void submit() } }}
                style={{ fontSize: 13.5, borderRadius: 9, padding: '10px 12px' }} />
            </label>
            {loginError && <div className="form-err">{loginError}</div>}
            <button className="btn btn-primary" disabled={busy}
              style={{ borderRadius: 9, padding: 11, fontSize: 14, fontWeight: 700, marginTop: 2, opacity: busy ? .6 : 1 }}
              onClick={() => void submit()}>
              {busy ? '로그인 중…' : '로그인'}
            </button>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, margin: '20px 0 12px' }}>
          <span style={{ flex: 1, height: 1, background: 'var(--border)' }} />
          <span className="muted2" style={{ fontSize: 11.5, fontWeight: 600 }}>시드 계정 — 클릭하면 바로 로그인</span>
          <span style={{ flex: 1, height: 1, background: 'var(--border)' }} />
        </div>

        <div style={{ display: 'grid', gap: 8 }}>
          {DEMO_ACCOUNTS.map((account) => (
            <div key={account.email}
              style={{ display: 'flex', alignItems: 'center', gap: 11, background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 11, padding: '10px 14px' }}>
              <span style={{ width: 32, height: 32, borderRadius: '50%', background: account.accent[0], color: account.accent[1], display: 'grid', placeItems: 'center', fontWeight: 700, fontSize: 12.5, flex: 'none' }}>
                {account.name.slice(0, 1)}
              </span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 13, fontWeight: 700 }}>
                  {account.name} <span className="muted" style={{ fontWeight: 500 }}>{account.role}</span>
                </div>
                <div className="muted2" style={{ fontSize: 11.5 }}>
                  {account.email} · 비밀번호 {DEMO_PASSWORD}
                </div>
              </div>
              <button className="btn btn-ghost btn-sm" disabled={busy}
                title="로그인 없이 이 화자로 시작 (인증 OFF 전용)"
                onClick={() => void enterWithoutLogin(account.personId)}>
                바로 시작
              </button>
              <button className="btn btn-primary btn-sm" disabled={busy}
                onClick={() => void submit(account.email, DEMO_PASSWORD)}>
                로그인
              </button>
            </div>
          ))}
        </div>

        {/* 로그인 우회 — 인증이 꺼진 개발 환경에서 화자만 지정해 둘러본다 */}
        <div className="card" style={{ marginTop: 18, padding: '14px 16px', borderStyle: 'dashed' }}>
          <div style={{ fontSize: 12.5, fontWeight: 700, marginBottom: 4 }}>
            로그인 없이 둘러보기 <span className="badge" style={{ background: 'var(--confirm-bg)', color: 'var(--confirm-text)', border: '1px solid var(--confirm-border)', marginLeft: 4 }}>개발 모드</span>
          </div>
          <div className="muted2" style={{ fontSize: 11.5, marginBottom: 10 }}>
            백엔드 <span className="code">pms.auth.enabled=false</span>(기본값)일 때만 통합니다 —
            화자를 <span className="code">X-Caller-Person-Id</span> 헤더로 보냅니다. 인증을 켜면
            서버가 401로 막습니다.
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span className="muted" style={{ fontSize: 12, fontWeight: 600 }}>personId</span>
            <input type="number" min="1" value={callerId} disabled={busy}
              onChange={(e) => setCallerId(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') { void enterWithoutLogin(Number(callerId)) } }}
              style={{ width: 90, textAlign: 'right' }} />
            <button className="btn btn-ghost" disabled={busy}
              onClick={() => void enterWithoutLogin(Number(callerId))}>
              이 화자로 시작
            </button>
            <span className="muted2" style={{ fontSize: 11.5 }}>시드 인원 1~43</span>
          </div>
        </div>
      </div>
    </div>
  )
}
