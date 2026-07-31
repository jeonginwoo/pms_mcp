import { useEffect, useRef } from 'react'
import { useStore } from '../store.jsx'

const DOWN_REASONS = ['부정확', '못 알아들음', '느림', '기타']

export default function ChatPanel() {
  const st = useStore()
  const { S, setState, user, role, sendChat, sendFeedback, runConfirm, visibleProjects, canEdit } = st
  const bodyRef = useRef(null)

  useEffect(() => {
    if (bodyRef.current) bodyRef.current.scrollTop = bodyRef.current.scrollHeight
  }, [S.chatMsgs, S.chatBusy, S.chatConfirm])

  if (!S.chatOpen) return null
  const u = user(), r = role()

  // 역할별 추천 질문 — member는 본인이 수정 가능한 프로젝트 기준으로 생성
  let suggestions
  if (r === 'member') {
    const editable = visibleProjects().find(p => p.status === '진행중' && canEdit(p))
    const word = editable ? editable.name.split(' ')[0] : '내'
    const to = editable ? Math.min(100, editable.progress + 5) : 80
    suggestions = ['내가 참여 중인 프로젝트 알려줘', `${word} 프로젝트 진행률 ${to}로 올려줘`, '이번 달 내 가동률은?']
  } else {
    suggestions = ['이번 달 오버부킹 누구야?', '다음 달에 여유 있는 사람 있어?', '유지보수 장애 이력 요약해줘']
  }

  const c = S.chatConfirm

  return (
    <aside className="chat-panel">
      <div className="chat-head">
        <div>
          <div style={{ fontWeight: 800, fontSize: 14 }}>AI 어시스턴트</div>
          <div className="muted2" style={{ fontSize: 11.5 }}>{u.name} 권한으로 질의 · MCP 도구 연결</div>
        </div>
        <button className="btn btn-ghost" style={{ width: 28, height: 28, padding: 0 }} onClick={() => setState({ chatOpen: false })}>✕</button>
      </div>

      <div className="chat-body" ref={bodyRef}>
        {S.chatMsgs.length === 0 && !S.chatBusy && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'flex-start' }}>
            <p className="muted" style={{ margin: '0 0 2px', fontSize: 13 }}>PMS 데이터를 자연어로 조회하세요. 예를 들어:</p>
            {suggestions.map(s => <button key={s} className="suggest" onClick={() => sendChat(s)}>{s}</button>)}
          </div>
        )}

        {S.chatMsgs.map((m, i) => {
          const isUser = m.role === 'user'
          return (
            <div key={i} style={{ display: 'flex', flexDirection: 'column', maxWidth: '88%', alignSelf: isUser ? 'flex-end' : 'flex-start', alignItems: isUser ? 'flex-end' : 'flex-start' }}>
              <div className={`bubble ${isUser ? 'user' : 'ai'}`}>{m.text}</div>
              {!isUser && !m.fb && S.reasonFor !== i && (
                <div style={{ display: 'flex', gap: 4, marginTop: 4 }}>
                  <button className="fb-btn" onClick={() => { sendFeedback('up', null); setState(s => ({ chatMsgs: s.chatMsgs.map((x, j) => j === i ? { ...x, fb: 'up' } : x) })) }}>👍</button>
                  <button className="fb-btn" onClick={() => setState({ reasonFor: i })}>👎</button>
                </div>
              )}
              {S.reasonFor === i && (
                <div style={{ display: 'flex', gap: 4, marginTop: 4, alignItems: 'center', flexWrap: 'wrap' }}>
                  <span className="muted2" style={{ fontSize: 11.5 }}>이유:</span>
                  {DOWN_REASONS.map(rr => (
                    <button key={rr} className="reason-chip" onClick={() => { sendFeedback('down', rr); setState(s => ({ reasonFor: null, chatMsgs: s.chatMsgs.map((x, j) => j === i ? { ...x, fb: 'down', fbReason: rr } : x) })) }}>{rr}</button>
                  ))}
                </div>
              )}
              {!isUser && m.fb && (
                <div className="muted2" style={{ fontSize: 11.5, marginTop: 4 }}>
                  {m.fb === 'up' ? '👍 피드백 감사합니다' : `👎 피드백이 수집되었습니다${m.fbReason ? ' · ' + m.fbReason : ''}`}
                </div>
              )}
            </div>
          )
        })}

        {c && (
          <div className="confirm-card" style={{ alignSelf: 'flex-start', maxWidth: '88%', animation: 'pmsslide .18s ease' }}>
            <div className="t" style={{ fontSize: 12.5 }}>확인 필요 — 아직 실행되지 않았습니다</div>
            <div style={{ fontSize: 13, marginBottom: 10 }}>[{c.name}] 진행률 {c.from}% → {c.to}% 변경 (v{c.version} 기준). 실행할까요?</div>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button className="btn btn-ghost" style={{ padding: '6px 13px', fontSize: 12 }}
                onClick={() => setState(s => ({ chatConfirm: null, chatMsgs: [...s.chatMsgs, { role: 'ai', text: '취소했습니다. 변경 사항은 없습니다.', fb: null }] }))}>취소</button>
              <button className="btn btn-primary" style={{ padding: '6px 15px', fontSize: 12, fontWeight: 700 }} onClick={runConfirm}>실행</button>
            </div>
          </div>
        )}

        {S.chatBusy && (
          <div style={{ alignSelf: 'flex-start', background: 'var(--soft)', border: '1px solid var(--border)', borderRadius: 14, borderBottomLeftRadius: 4, padding: '10px 14px', fontSize: 12.5, color: 'var(--muted)' }}>
            <span>{S.chatBusyText}</span>
            <span style={{ display: 'inline-block', animation: 'pmsblink 1s infinite', marginLeft: 2 }}>●</span>
          </div>
        )}
      </div>

      <div className="chat-input-row">
        <input value={S.chatInput} placeholder="메시지를 입력하세요 (최대 2,000자)" maxLength={2000}
          onChange={e => setState({ chatInput: e.target.value })}
          onKeyDown={e => { if (e.key === 'Enter') sendChat() }}
          style={{ flex: 1, background: 'var(--soft)', borderRadius: 10, padding: '9px 12px' }} />
        <button className="btn btn-primary" style={{ borderRadius: 10, fontWeight: 700, opacity: S.chatInput.trim() && !S.chatBusy ? 1 : .5 }} onClick={() => sendChat()}>보내기</button>
      </div>
    </aside>
  )
}
