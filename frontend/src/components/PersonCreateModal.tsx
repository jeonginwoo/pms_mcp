/*
 * 인력 등록 (AC E2-1) — 관리 권한자만 보는 모달.
 *
 * 로그인 계정이 함께 만들어진다: 이메일이 로그인 ID이고 초기 비밀번호는 서버가
 * 정한 공용값(부록 B)이라 입력에 없다. 조직·직급·권한 그룹은 서버가 준 목록에서
 * 고른다 — 없는 id를 보내면 422다.
 */
import { useState } from 'react'
import { useStore } from '../store'
import { ErrorText, Field, Modal } from './ui'

export default function PersonCreateModal({ onClose }: { onClose: () => void }) {
  const { orgUnits, grades, permissionGroups, createPerson, showToast } = useStore()
  const [form, setForm] = useState({
    name: '',
    email: '',
    orgUnitId: '',
    gradeId: '',
    groupId: '',
  })
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)

  const set = (patch: Partial<typeof form>) => setForm((current) => ({ ...current, ...patch }))

  const submit = async () => {
    if (!form.orgUnitId || !form.gradeId || !form.groupId) {
      setError({ code: 'VALIDATION_ERROR', message: '조직·직급·권한 그룹을 모두 선택해 주세요' })

      return
    }

    setBusy(true)
    setError(null)
    const result = await createPerson({
      name: form.name.trim(),
      email: form.email.trim(),
      orgUnitId: Number(form.orgUnitId),
      gradeId: Number(form.gradeId),
      groupId: Number(form.groupId),
    })
    setBusy(false)

    if (result.ok) {
      showToast(`${result.value.name}님을 등록했습니다 (초기 비밀번호 proten1!)`)
      onClose()

      return
    }

    setError({ code: result.error.code, message: result.error.message })
  }

  return (
    <Modal title="인력 등록" onClose={onClose}>
      <div style={{ display: 'grid', gap: 12 }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Field label="이름">
            <input value={form.name} autoFocus onChange={(e) => set({ name: e.target.value })} />
          </Field>
          <Field label="이메일" hint="로그인 ID">
            <input type="email" value={form.email} placeholder="name@proten.co.kr"
              onChange={(e) => set({ email: e.target.value })} />
          </Field>
        </div>

        <Field label="소속 조직">
          <select value={form.orgUnitId} onChange={(e) => set({ orgUnitId: e.target.value })}>
            <option value="">조직 선택…</option>
            {orgUnits.map((unit) => (
              <option key={unit.id} value={unit.id}>{unit.name}</option>
            ))}
          </select>
        </Field>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Field label="직급">
            <select value={form.gradeId} onChange={(e) => set({ gradeId: e.target.value })}>
              <option value="">직급 선택…</option>
              {grades.map((grade) => (
                <option key={grade.id} value={grade.id}>{grade.name}</option>
              ))}
            </select>
          </Field>
          <Field label="권한 그룹" hint="가시성·기능 권한">
            <select value={form.groupId} onChange={(e) => set({ groupId: e.target.value })}>
              <option value="">그룹 선택…</option>
              {permissionGroups.map((group) => (
                <option key={group.id} value={group.id}>{group.name}</option>
              ))}
            </select>
          </Field>
        </div>

        <div className="muted2" style={{ fontSize: 11.5 }}>
          초기 비밀번호는 <span className="code">proten1!</span>입니다 — 첫 로그인 후 변경하세요.
          월 가용 M/M은 1.0, 가동률 집계 대상으로 시작합니다.
        </div>

        {error && <ErrorText code={error.code} message={error.message} />}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 4 }}>
          <button className="btn btn-ghost" onClick={onClose}>취소</button>
          <button className="btn btn-primary" disabled={busy} onClick={() => void submit()}>
            {busy ? '등록 중…' : '등록'}
          </button>
        </div>
      </div>
    </Modal>
  )
}
