// 로그인 — email/password (초기 비밀번호 proten1!) + 검증용 데모 계정
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { login, loginAs, useApp } from '../core/store'

const DEMO = [
  { id: 100, note: '시스템 관리자 — 회사 고유 ADMIN 계정(삭제 불가)' },
  { id: 1, note: 'ADMIN(대표) — 전사 가시성·조직 관리' },
  { id: 13, note: '부문장(AX솔루션) — 부문 가시성·다수 PM' },
  { id: 16, note: '팀장(AX솔루션개발1팀) — 팀 가시성·생성 권한' },
  { id: 18, note: '팀원 — PM인데 orgRole=MEMBER (키스톤 페르소나)' },
  { id: 19, note: '팀원 — 참여자만 (최소 권한)' },
]

export default function Login() {
  const s = useApp()
  const nav = useNavigate()
  const [email, setEmail] = useState('')
  const [pw, setPw] = useState('')
  const [msg, setMsg] = useState('')

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    const r = login(email.trim(), pw)
    if (!r.ok) { setMsg(r.message); return }
    nav('/')
  }
  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={submit}>
        <h1>PROTEN PMS</h1>
        <p className="page-desc">화면 프로토타입 — 기획 검증용 (목업 데이터)</p>
        <div className="field">
          <label>이메일</label>
          <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="name@proten.co.kr" />
        </div>
        <div className="field">
          <label>비밀번호 (전 계정 공통: proten1!)</label>
          <input type="password" value={pw} onChange={(e) => setPw(e.target.value)} />
        </div>
        {msg && <div className="error-box">{msg}</div>}
        <button className="btn primary" style={{ width: '100%' }}>로그인</button>
        <div className="demo-accounts">
          <div style={{ fontSize: 12, color: 'var(--muted)', marginBottom: 8 }}>데모 계정으로 바로 들어가기 (권한 검증용)</div>
          {DEMO.map((d) => {
            const p = s.people.find((x) => x.id === d.id)!
            return (
              <button key={d.id} type="button" className="btn sm" onClick={() => { loginAs(d.id); nav('/') }}>
                <b>{p.name}</b> — {d.note}
              </button>
            )
          })}
        </div>
      </form>
    </div>
  )
}
