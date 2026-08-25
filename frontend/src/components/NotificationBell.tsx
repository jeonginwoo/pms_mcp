/*
 * 알림 벨 (부록 A 공통 헤더 · AC F1-3 · H1-4).
 *
 * 부록 A는 "미읽음 수 — SSE 즉시 갱신 · 클릭 시 목록·읽음 처리"를 요구한다.
 * **SSE는 아직 서버에 없다**(F1-4 — `?access_token=` 인증과 로그 마스킹이 한 묶음이라
 * 라우트를 먼저 열면 토큰이 액세스 로그로 샌다). 그래서 지금은 부팅과 **벨을 열 때**
 * 다시 읽는다. 폴링을 두지 않은 이유는 44명 규모에 끊임없는 요청이 생기고, 그것이
 * SSE가 하려던 일을 어설프게 흉내 내기 때문이다 — 스트림이 열리면 이 자리가 구독으로
 * 바뀐다(그때 이 주석도 함께 지운다).
 *
 * 설정(H1-4)은 같은 패널에서 연다: 알림을 보다가 "이건 그만 받고 싶다"가 나오는
 * 자리가 여기이고, 별 화면으로 빼면 그 순간에 도달할 경로가 없다.
 */
import { useEffect, useRef, useState } from 'react'
import { useStore } from '../store'
import { NOTIFICATION_TYPE_LABEL } from '../labels'
import NotificationPrefsModal from './NotificationPrefsModal'

export default function NotificationBell() {
  const { notifications, unreadNotifications, markNotificationRead, showToast }
    = useStore()
  const [open, setOpen] = useState(false)
  const [prefsOpen, setPrefsOpen] = useState(false)
  const wrap = useRef<HTMLDivElement>(null)

  // 열 때 재조회하지 않는다(2026-08-25): SSE가 열려 있는 동안 새 알림이 이미
  // 흘러들고, 재연결 시 끊겨 있던 동안의 것은 서버가 재생한다(F1-4). 열 때마다
  // 읽으면 스트림이 채운 목록을 같은 내용으로 덮어쓰는 왕복이 하나 더 생긴다

  // 바깥을 누르면 닫는다 — 드롭다운의 기본 기대 동작이다
  useEffect(() => {
    if (!open) {
      return
    }

    const close = (event: MouseEvent) => {
      if (wrap.current && !wrap.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }

    document.addEventListener('mousedown', close)

    return () => document.removeEventListener('mousedown', close)
  }, [open])

  const read = async (notificationId: number) => {
    const result = await markNotificationRead(notificationId)

    if (!result.ok) {
      showToast(result.error.message)
    }
  }

  return (
    <div className="bell-wrap" ref={wrap}>
      <button
        className="icon-btn"
        title="알림"
        aria-label={`알림 ${unreadNotifications}건 미읽음`}
        onClick={() => setOpen((current) => !current)}
      >
        🔔
        {unreadNotifications > 0 && <span className="bell-cnt">{unreadNotifications}</span>}
      </button>

      {open && (
        <div className="bell-panel">
          <div className="bell-head">
            <strong style={{ fontSize: 13 }}>알림 {unreadNotifications > 0
              && <span className="muted2">· 미읽음 {unreadNotifications}</span>}</strong>
            <button
              className="btn btn-ghost btn-sm"
              onClick={() => { setPrefsOpen(true); setOpen(false) }}
            >
              설정
            </button>
          </div>

          {notifications.length === 0 && (
            <div className="empty" style={{ padding: '22px 16px' }}>알림이 없습니다</div>
          )}

          <div style={{ maxHeight: 380, overflowY: 'auto' }}>
            {notifications.map((notification) => (
              <div
                key={notification.id}
                className={`bell-item ${notification.read ? '' : 'unread'}`}
              >
                {!notification.read && <span className="bell-dot" />}
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div className="muted2" style={{ fontSize: 11 }}>
                    {NOTIFICATION_TYPE_LABEL[notification.type] ?? notification.type}
                    {' · '}
                    {notification.createdAt.slice(0, 16).replace('T', ' ')}
                  </div>
                  <div style={{ fontSize: 12.5, marginTop: 2 }}>{notification.message}</div>
                </div>
                {!notification.read && (
                  <button
                    className="btn btn-ghost btn-sm"
                    onClick={() => void read(notification.id)}
                  >
                    읽음
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {prefsOpen && <NotificationPrefsModal onClose={() => setPrefsOpen(false)} />}
    </div>
  )
}
