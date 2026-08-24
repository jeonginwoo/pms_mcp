/*
 * 역할 변경 확인 (AC A6-1·A6-3·A6-6·A6-7) — 배정 패널의 드롭다운이 여는 모달.
 *
 * **경로가 둘인 것이 이 모달의 요점이다.** 서버는 PM을 전용 라우트로 가른다(A6-7):
 * "프로젝트당 PM 1행" 불변식(A6-5)은 새 PM 승격과 직전 PM 강등을 한 트랜잭션에서 해야
 * 성립하므로, 역할 하나만 바꾸는 `/roles`로는 PM을 만들 수 없다. 그래서 PM을 고르면
 * `changeManager`(PUT /pm), PL·참여자는 `changeRole`(PUT /roles)로 간다.
 *
 * 한 번 더 확인을 받는 이유(사용자 결정 2026-08-24): 역할 변경은 그 프로젝트에서 할 수
 * 있는 일을 바꾼다(§4-2 매트릭스). 특히 PM 지정은 **직전 PM을 참여자로 내리는 부수효과**가
 * 있어서, 드롭다운을 고르는 순간 저장되면 무엇이 함께 바뀌는지 볼 자리가 없다.
 */
import { useState } from 'react'
import { useStore } from '../store'
import { ROLE_LABEL } from '../labels'
import { ErrorText, Modal, ModalActions } from './ui'
import type { AssignmentView, ProjectRole } from '../types/api'

export default function RoleChangeModal({ assignment, nextRole, onClose }: {
  assignment: AssignmentView
  nextRole: ProjectRole
  onClose: () => void
}) {
  const { detail, changeManager, changeRole, showToast } = useStore()
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)

  const name = assignment.personName ?? `#${assignment.personId}`
  const currentManager = detail?.assignments.find((row) => row.role === 'PM')

  const submit = async () => {
    setBusy(true)
    const result = nextRole === 'PM'
      ? await changeManager(assignment.personId)
      : await changeRole(assignment.personId, nextRole)
    setBusy(false)

    if (result.ok) {
      showToast(`${name}님의 역할을 ${ROLE_LABEL[nextRole]}로 바꿨습니다`)
      onClose()

      return
    }

    setError({ code: result.error.code, message: result.error.message })
  }

  return (
    <Modal title="역할 변경 확인" onClose={onClose}>
      <div style={{ display: 'grid', gap: 10, fontSize: 13 }}>
        <div>
          <strong>{name}</strong>님의 역할을{' '}
          <strong>{ROLE_LABEL[assignment.role]} → {ROLE_LABEL[nextRole]}</strong>로 바꿉니다.
        </div>

        {/* PM 지정의 부수효과 — 직전 PM은 참여자로 내려간다(A6-4). 배정은 유지된다 */}
        {nextRole === 'PM' && currentManager && (
          <div className="muted" style={{ fontSize: 12.5 }}>
            현재 PM {currentManager.personName ?? `#${currentManager.personId}`}님은
            {' '}참여자로 내려갑니다 — 배정과 M/M은 그대로 남습니다.
          </div>
        )}

        {/* 해제의 의미를 적어 둔다: 배정을 끊는 것과 다른 행위다(US-B2) */}
        {nextRole === 'PARTICIPANT' && assignment.role === 'PL' && (
          <div className="muted" style={{ fontSize: 12.5 }}>
            PL 해제입니다 — 배정은 유지되고, 배정을 끝내려면 목록의 종료(×)를 쓰세요.
          </div>
        )}

        {!assignment.personActive && (
          <div className="muted" style={{ fontSize: 12.5 }}>
            이 인원은 퇴사 처리된 계정입니다 — 지정 전에 대상이 맞는지 확인하세요.
          </div>
        )}
      </div>

      {error && <ErrorText code={error.code} message={error.message} />}

      <ModalActions>
        <button className="btn btn-ghost" onClick={onClose}>취소</button>
        <button className="btn btn-primary" disabled={busy} onClick={() => void submit()}>
          {busy ? '변경 중…' : '변경'}
        </button>
      </ModalActions>
    </Modal>
  )
}
