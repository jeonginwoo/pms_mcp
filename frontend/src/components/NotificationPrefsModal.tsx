/*
 * 내 알림 설정 (AC H1-4).
 *
 * 설정 단위는 `NotificationType` 5종이다(2026-08-24 확정 — 구 `{progress, project, org,
 * weekly}` 네 칸은 알림 유형이 정해지기 전의 이름이다). **유형 목록을 화면이 열거하지
 * 않는다**: 서버가 언제나 전체를 담아 주므로 유형이 늘어도 화면이 따라올 필요가 없다.
 *
 * 저장은 **전체 교체**다(§7 PUT) — 켠 것만 보내면 서버가 나머지를 "그대로"로 읽어
 * 두 쪽이 갈린다.
 *
 * 끈 유형은 "숨김"이 아니라 "안 만듦"이다(F1-5): 껐던 동안의 알림은 나중에 켜도
 * 오지 않는다. 그 사실을 화면이 한 줄로 말해 준다 — 모르면 버그로 읽힌다.
 */
import { useEffect, useState } from 'react'
import { useStore } from '../store'
import { NOTIFICATION_TYPE_LABEL } from '../labels'
import { ErrorText, Modal, ModalActions } from './ui'
import type { ApiError } from '../api'
import type { NotificationType } from '../types/api'

export default function NotificationPrefsModal({ onClose }: { onClose: () => void }) {
  const { loadNotifPrefs, updateNotifPrefs, showToast } = useStore()
  const [enabled, setEnabled] = useState<Record<NotificationType, boolean> | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    void (async () => {
      const result = await loadNotifPrefs()

      if (result.ok) {
        setEnabled(result.value.enabled)

        return
      }

      setError(result.error)
    })()
  }, [loadNotifPrefs])

  const save = async () => {
    if (!enabled) {
      return
    }

    setSaving(true)
    const result = await updateNotifPrefs(enabled)
    setSaving(false)

    if (!result.ok) {
      setError(result.error)

      return
    }

    showToast('알림 설정을 저장했습니다')
    onClose()
  }

  return (
    <Modal title="알림 설정" width={440} onClose={onClose}>
      <p className="muted2" style={{ fontSize: 12, marginTop: -6 }}>
        끄면 그 유형의 알림이 <strong>만들어지지 않습니다</strong> — 나중에 다시 켜도
        꺼 둔 동안의 알림은 오지 않습니다.
      </p>

      {!enabled && !error && <div className="empty">불러오는 중…</div>}

      {enabled && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 2, marginTop: 10 }}>
          {(Object.keys(enabled) as NotificationType[]).map((type) => (
            <label
              key={type}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '9px 2px',
                fontSize: 13,
              }}
            >
              <input
                type="checkbox"
                checked={enabled[type]}
                onChange={(e) => setEnabled({ ...enabled, [type]: e.target.checked })}
              />
              {NOTIFICATION_TYPE_LABEL[type] ?? type}
            </label>
          ))}
        </div>
      )}

      {error && <ErrorText code={error.code} message={error.message} />}

      <ModalActions>
        <button className="btn btn-ghost" onClick={onClose}>취소</button>
        <button className="btn btn-primary" disabled={!enabled || saving} onClick={() => void save()}>
          {saving ? '저장 중…' : '저장'}
        </button>
      </ModalActions>
    </Modal>
  )
}
