import { useStore } from '../store.jsx'

const NOTIF_ROWS = [
  ['progress', '진행률 변경 알림', '담당 프로젝트의 진행률이 변경되면 알림'],
  ['project', '프로젝트 생성·삭제 알림', '새 프로젝트 등록 및 삭제 시 알림'],
  ['org', '조직·사용자 변경 알림', '조직 구조와 사용자 정보 변경 시 알림'],
  ['weekly', '주간 리포트 이메일', '매주 월요일 진행 현황 요약을 이메일로 수신'],
]

/** 내 계정 모달 — 프로필 · 비밀번호 · 알림 설정 (프로토타입 사양, 서버 연동) */
export default function AccountModal() {
  const { S, setState, user, setAcct, saveAcct, notifPrefs, toggleNotifPref } = useStore()
  const m = S.acctModal
  if (!m) return null
  const u = user()
  const f = m.form
  const prefs = notifPrefs()
  const tabs = [['profile', '프로필'], ['password', '비밀번호'], ['notif', '알림 설정']]
  const field = { fontSize: 13.5, borderRadius: 9, padding: '9px 12px', width: '100%' }

  return (
    <div className="overlay">
      <div className="modal" style={{ width: 480 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <h3>내 계정</h3>
          <button style={{ background: 'none', border: 'none', color: 'var(--muted2)', fontSize: 18, lineHeight: 1, padding: '2px 6px' }} onClick={() => setState({ acctModal: null })}>×</button>
        </div>
        <div style={{ display: 'flex', gap: 6, borderBottom: '1px solid var(--border-soft)', marginBottom: 18 }}>
          {tabs.map(([k, label]) => {
            const on = m.tab === k
            return (
              <button key={k} onClick={() => setState(s => ({ acctModal: { ...s.acctModal, tab: k, err: null } }))}
                style={{ background: 'none', border: 'none', borderBottom: `2px solid ${on ? 'var(--primary)' : 'transparent'}`, color: on ? 'var(--primary)' : 'var(--muted)', fontSize: 13, fontWeight: on ? 700 : 500, padding: '8px 12px' }}>
                {label}
              </button>
            )
          })}
        </div>

        {m.tab === 'profile' && (
          <>
            <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 18 }}>
              <div className="avatar" style={{ width: 52, height: 52, fontWeight: 800, fontSize: 19 }}>{u.name?.[0]}</div>
              <div>
                <div style={{ fontWeight: 700, fontSize: 14.5 }}>{u.name} <span className="muted" style={{ fontWeight: 500 }}>{u.title}</span></div>
                <div className="muted2" style={{ fontSize: 12 }}>{u.team} · {u.grade}</div>
              </div>
            </div>
            <div style={{ display: 'grid', gap: 13 }}>
              <label style={{ display: 'grid', gap: 5 }}>
                <span className="muted" style={{ fontSize: 12, fontWeight: 600 }}>이름</span>
                <input value={f.name} onChange={e => setAcct('name', e.target.value)} style={field} />
              </label>
              <label style={{ display: 'grid', gap: 5 }}>
                <span className="muted" style={{ fontSize: 12, fontWeight: 600 }}>이메일 (계정 ID)</span>
                <input value={f.email} onChange={e => setAcct('email', e.target.value)} style={field} />
              </label>
              <label style={{ display: 'grid', gap: 5 }}>
                <span className="muted" style={{ fontSize: 12, fontWeight: 600 }}>연락처</span>
                <input value={f.phone} placeholder="010-0000-0000" onChange={e => setAcct('phone', e.target.value)} style={field} />
              </label>
              <div className="muted2" style={{ fontSize: 11.5 }}>소속·직급·권한은 관리자가 설정·관리에서 변경할 수 있습니다.</div>
            </div>
          </>
        )}

        {m.tab === 'password' && (
          <div style={{ display: 'grid', gap: 13 }}>
            <label style={{ display: 'grid', gap: 5 }}>
              <span className="muted" style={{ fontSize: 12, fontWeight: 600 }}>현재 비밀번호</span>
              <input type="password" value={f.pwCur} onChange={e => setAcct('pwCur', e.target.value)} style={field} />
            </label>
            <label style={{ display: 'grid', gap: 5 }}>
              <span className="muted" style={{ fontSize: 12, fontWeight: 600 }}>새 비밀번호</span>
              <input type="password" value={f.pwNew} placeholder="8자 이상" onChange={e => setAcct('pwNew', e.target.value)} style={field} />
            </label>
            <label style={{ display: 'grid', gap: 5 }}>
              <span className="muted" style={{ fontSize: 12, fontWeight: 600 }}>새 비밀번호 확인</span>
              <input type="password" value={f.pwNew2} onChange={e => setAcct('pwNew2', e.target.value)} style={field} />
            </label>
            <div className="muted2" style={{ fontSize: 11.5 }}>변경한 비밀번호는 다음 로그인부터 적용됩니다. (초기 비밀번호 proten1!)</div>
          </div>
        )}

        {m.tab === 'notif' && (
          <div style={{ display: 'grid', gap: 12 }}>
            {NOTIF_ROWS.map(([k, label, desc]) => (
              <div key={k} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>{label}</div>
                  <div className="muted2" style={{ fontSize: 11.5 }}>{desc}</div>
                </div>
                <button className={`switch ${prefs[k] ? 'on' : ''}`} onClick={() => toggleNotifPref(k)} aria-label={label}>
                  <span className="knob" />
                </button>
              </div>
            ))}
          </div>
        )}

        {m.err && <div className="form-err" style={{ marginTop: 14 }}>{m.err}</div>}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 20 }}>
          <button className="btn btn-ghost" onClick={() => setState({ acctModal: null })}>닫기</button>
          {m.tab !== 'notif' && <button className="btn btn-primary" onClick={saveAcct}>저장</button>}
        </div>
      </div>
    </div>
  )
}
