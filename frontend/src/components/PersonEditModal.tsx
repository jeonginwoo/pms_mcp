/*
 * 인력 수정 (AC E2-2) + 소속 이동 (AC E1-1·E1-2) — 관리 권한자 전용.
 *
 * **저장 경로가 둘인 것이 이 모달의 요점이다.** 서버가 소속 이동을 전용 라우트로
 * 가른 이유가 있다: 이동은 진행 중 배정이 있어도 허용하고 경고를 돌려주며(E1-2),
 * 과거 집계가 새 소속 기준으로 다시 계산된다는 파급이 그 행위에만 붙는다. 화면이
 * 그것을 일반 수정에 섞으면 경고가 사라진다.
 *
 * 그래서: **소속만 바뀌었으면 이동 라우트**, 그 밖이면 수정 라우트, 둘 다 바뀌었으면
 * 이동을 먼저 하고(경고를 받아 보여 준 뒤) 수정을 잇는다. 이동이 version을 올리므로
 * 순서가 반대면 수정이 409가 된다.
 *
 * 시스템 계정은 목록에 없으므로(서버가 제외) 여기까지 오지 않는다 — E2-5의 422는
 * 그래도 서버가 지킨다.
 */
import { useState } from 'react'
import { useStore } from '../store'
import { ErrorText, Field, Modal, ModalActions } from './ui'
import type { PersonSummary } from '../types/api'

export default function PersonEditModal({ person, onClose }: {
  person: PersonSummary
  onClose: () => void
}) {
  const { orgUnits, grades, permissionGroups, updatePerson, movePersonOrgUnit, showToast }
    = useStore()
  const [form, setForm] = useState({
    name: person.name,
    orgUnitId: String(person.orgUnitId),
    gradeId: String(person.gradeId),
    groupId: String(person.groupId),
  })
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [warning, setWarning] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const set = (patch: Partial<typeof form>) => setForm((current) => ({ ...current, ...patch }))
  const orgUnitChanged = Number(form.orgUnitId) !== person.orgUnitId
  const otherChanged = form.name.trim() !== person.name
    || Number(form.gradeId) !== person.gradeId
    || Number(form.groupId) !== person.groupId

  const submit = async () => {
    if (!orgUnitChanged && !otherChanged) {
      onClose()

      return
    }

    setBusy(true)
    setError(null)
    // 소속 이동이 먼저다 — version을 올리므로 순서가 반대면 뒤의 수정이 409가 된다
    let version = person.version

    if (orgUnitChanged) {
      const moved = await movePersonOrgUnit(person.id, Number(form.orgUnitId))

      if (!moved.ok) {
        setBusy(false)
        setError({ code: moved.error.code, message: moved.error.message })

        return
      }

      version = moved.value.person.version
      setWarning(moved.value.warning)

      // 소속만 바뀌었으면 여기서 끝이다 — 경고를 읽을 시간을 주려고 닫지 않는다
      if (!otherChanged) {
        setBusy(false)
        showToast(`${moved.value.person.name}님의 소속을 옮겼습니다`)

        if (!moved.value.warning) {
          onClose()
        }

        return
      }
    }

    const result = await updatePerson(person.id, {
      name: form.name.trim(),
      orgUnitId: Number(form.orgUnitId),
      gradeId: Number(form.gradeId),
      groupId: Number(form.groupId),
      version,
    })
    setBusy(false)

    if (!result.ok) {
      setError({ code: result.error.code, message: result.error.message })

      return
    }

    showToast(`${result.value.name}님의 정보를 수정했습니다`)

    if (!warning) {
      onClose()
    }
  }

  return (
    <Modal title={`인력 수정 — ${person.name}`} onClose={onClose}>
      <div style={{ display: 'grid', gap: 12 }}>
        <Field label="이름">
          <input value={form.name} autoFocus disabled={busy}
            onChange={(e) => set({ name: e.target.value })} />
        </Field>

        <Field label="소속 조직" hint="바꾸면 가시성이 즉시 따라 바뀝니다">
          <select value={form.orgUnitId} disabled={busy}
            onChange={(e) => set({ orgUnitId: e.target.value })}>
            {orgUnits.map((unit) => (
              <option key={unit.id} value={unit.id}>{unit.name}</option>
            ))}
          </select>
        </Field>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Field label="직급">
            <select value={form.gradeId} disabled={busy}
              onChange={(e) => set({ gradeId: e.target.value })}>
              {grades.map((grade) => (
                <option key={grade.id} value={grade.id}>{grade.name}</option>
              ))}
            </select>
          </Field>
          <Field label="권한 그룹" hint="가시성·기능 권한이 바뀝니다">
            <select value={form.groupId} disabled={busy}
              onChange={(e) => set({ groupId: e.target.value })}>
              {permissionGroups.map((group) => (
                <option key={group.id} value={group.id}>{group.name}</option>
              ))}
            </select>
          </Field>
        </div>
      </div>

      {warning && (
        <div className="save-msg err" style={{ background: 'var(--warn-soft, rgba(185,130,15,.13))', borderColor: 'rgba(185,130,15,.4)', color: 'var(--warn, #b9820f)' }}>
          {warning}
        </div>
      )}

      {error && <ErrorText code={error.code} message={error.message} />}

      <ModalActions>
        <button className="btn btn-ghost" onClick={onClose}>
          {warning ? '닫기' : '취소'}
        </button>
        <button className="btn btn-primary" disabled={busy} onClick={() => void submit()}>
          {busy ? '저장 중…' : '저장'}
        </button>
      </ModalActions>
    </Modal>
  )
}
