/*
 * 내 계정 (EPIC H — H1-1 상세 · H1-2 프로필 · H1-3 비밀번호).
 *
 * **두 폼을 한 모달에 두되 저장 버튼은 각각이다**: 프로필과 비밀번호는 서버에서도
 * 다른 라우트이고(전자는 person, 후자는 auth), 실패 조건도 다르다(409 중복 vs 400
 * 불일치). 버튼 하나로 묶으면 "이름만 고치려는데 비밀번호를 왜 묻나"가 된다.
 *
 * **소속·직급·권한 그룹은 없다**: 그것은 관리자 경로(E2-2)의 몫이다. 여기 열면 자기
 * 권한 그룹을 스스로 바꿀 수 있게 된다 — 서버도 같은 이유로 그 칸을 받지 않는다.
 *
 * **비밀번호 오류를 갈라 보여 주지 않는다**: 서버가 현재 비밀번호 불일치와 새
 * 비밀번호 형식 오류를 같은 400으로 수렴시키고(H1-3), 화면이 그것을 갈라 주면
 * "현재 비밀번호는 맞았다"가 다시 새어 나간다.
 */
import { useEffect, useState } from 'react'
import { useStore } from '../store'
import { ErrorText, Field, Modal, ModalActions } from './ui'

export default function MyAccountModal({ onClose }: { onClose: () => void }) {
  const { loadMyAccount, updateProfile, changePassword, showToast } = useStore()
  const [form, setForm] = useState<{ name: string; email: string; phone: string } | null>(null)
  const [secret, setSecret] = useState({ current: '', newPassword: '' })
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let live = true
    void loadMyAccount().then((result) => {
      if (live && result.ok) {
        setForm({
          name: result.value.name,
          email: result.value.email ?? '',
          phone: result.value.phone ?? '',
        })
      }
    })

    return () => { live = false }
  }, [loadMyAccount])

  const saveProfile = async () => {
    if (!form) {
      return
    }

    setBusy(true)
    setError(null)
    const result = await updateProfile({
      name: form.name.trim(),
      email: form.email.trim(),
      // 없는 것이 정상 상태다 — 빈 칸은 "없음"으로 보낸다
      phone: form.phone.trim() === '' ? null : form.phone.trim(),
    })
    setBusy(false)

    if (result.ok) {
      showToast('프로필을 저장했습니다')

      return
    }

    setError(result.error)
  }

  const savePassword = async () => {
    setBusy(true)
    setError(null)
    const result = await changePassword(secret)
    setBusy(false)

    if (result.ok) {
      setSecret({ current: '', newPassword: '' })
      showToast('비밀번호를 바꿨습니다')

      return
    }

    setError(result.error)
  }

  return (
    <Modal title="내 계정" width={520} onClose={onClose}>
      {form === null && <div className="muted">불러오는 중…</div>}

      {form !== null && (
        <>
          <Field label="이름">
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </Field>

          <Field label="이메일" hint="로그인 ID입니다 — 바꾸면 다음 로그인부터 그 값입니다">
            <input value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })} />
          </Field>

          <Field label="전화번호" hint="비워 둘 수 있습니다">
            <input value={form.phone}
              onChange={(e) => setForm({ ...form, phone: e.target.value })} />
          </Field>

          <ModalActions>
            <button className="btn btn-primary btn-sm" disabled={busy}
              onClick={() => void saveProfile()}>
              {busy ? '저장 중…' : '프로필 저장'}
            </button>
          </ModalActions>

          <div style={{ marginTop: 18, borderTop: '1px solid var(--border-soft)',
            paddingTop: 14 }}>
            <div className="muted" style={{ fontWeight: 600, fontSize: 12.5, marginBottom: 8 }}>
              비밀번호 변경
            </div>

            <Field label="현재 비밀번호">
              <input type="password" value={secret.current}
                onChange={(e) => setSecret({ ...secret, current: e.target.value })} />
            </Field>

            <Field label="새 비밀번호" hint="8자 이상">
              <input type="password" value={secret.newPassword}
                onChange={(e) => setSecret({ ...secret, newPassword: e.target.value })} />
            </Field>

            <ModalActions>
              <button className="btn btn-sm" disabled={busy
                  || secret.current === '' || secret.newPassword === ''}
                onClick={() => void savePassword()}>
                비밀번호 변경
              </button>
            </ModalActions>
          </div>
        </>
      )}

      {error && <ErrorText code={error.code} message={error.message} />}
    </Modal>
  )
}
