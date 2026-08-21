/*
 * 프로젝트 상세 — 기본정보 + (진행중부터) 진척률 + 상태 행위 한 줄 + 배정.
 *
 * 진척률 섹션은 **계약대기·수주확정에서 아예 그리지 않는다**(2026-08-22 사용자 결정):
 * 그 단계에는 기록할 진척이 없고(A2-9로 서버도 거절한다) 못 만지는 편집기를 보여 주면
 * 화면이 할 일을 흐린다. 그 두 상태에서 지금 할 일은 다음 단계로 넘기는 것이고,
 * 그 버튼은 완료 처리·재개와 같은 자리(`ProjectActions`)에 있다.
 *
 * 버튼 노출은 permissions.ts의 표시용 판정을 따르고, 실제 판정은 서버가 한다 —
 * 서버가 돌려준 403/404/409는 그대로 보여 준다.
 */
import { useState } from 'react'
import { useStore } from '../store'
import { ENGAGEMENT_LABEL, PHASE_LABEL, ROLE_LABEL, dday, period } from '../labels'
import { canAssign, canDelete, canEditInfo, myRole } from '../permissions'
import { ErrorText, Metric, StatusBadge } from '../components/ui'
import ProgressEditor from '../components/ProgressEditor'
import ProjectActions from '../components/ProjectActions'
import AssignmentPanel from '../components/AssignmentPanel'
import ProjectEditModal from '../components/ProjectEditModal'

export default function ProjectDetail() {
  const { detail, me, closeProject, deleteProject, showToast } = useStore()
  const [editing, setEditing] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)

  if (!detail) {
    return null
  }

  const deadline = dday(detail.endDate)
  const role = myRole(me, detail)
  // 진척률은 진행중부터 의미가 있다 — 완료·유지보수중은 읽기 전용으로 남는다
  const showProgress = detail.status !== 'CONTRACT_PENDING' && detail.status !== 'ORDER_CONFIRMED'

  const runDelete = async () => {
    setBusy(true)
    setError(null)
    const result = await deleteProject()
    setBusy(false)
    setConfirmDelete(false)

    if (result.ok) {
      showToast('삭제되었습니다')

      return
    }

    setError({ code: result.error.code, message: result.error.message })
  }

  return (
    <div>
      <button onClick={closeProject}
        style={{ display: 'inline-flex', alignItems: 'center', gap: 6, background: 'none', border: 'none', color: 'var(--muted)', fontSize: 12.5, padding: 0, marginBottom: 12 }}>
        ← 프로젝트 목록
      </button>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1.7fr) minmax(320px,1fr)', gap: 16, alignItems: 'start' }}>
        <div style={{ display: 'grid', gap: 16 }}>
          <section className="card" style={{ padding: '22px 24px' }}>
            <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 14, marginBottom: 16 }}>
              <div>
                <h2 style={{ margin: '0 0 4px', fontSize: 18, letterSpacing: '-.3px' }}>{detail.name}</h2>
                <div className="muted" style={{ fontSize: 13 }}>
                  {detail.client}
                  {detail.phase && ` · ${PHASE_LABEL[detail.phase]}`}
                  {role && ` · 내 역할 ${ROLE_LABEL[role]}`}
                </div>
              </div>
              <div style={{ flex: 'none', display: 'flex', alignItems: 'center', gap: 8 }}>
                <StatusBadge status={detail.status} big />
                {canEditInfo(me, detail) && (
                  <button className="btn btn-ghost btn-sm" style={{ padding: '6px 12px', fontSize: 12 }}
                    onClick={() => setEditing(true)}>
                    정보 수정
                  </button>
                )}
                {canDelete(me, detail) && (
                  <button className="btn btn-danger-ghost btn-sm"
                    style={{ padding: '6px 12px', fontSize: 12 }}
                    onClick={() => setConfirmDelete(true)}>
                    삭제
                  </button>
                )}
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 10, marginBottom: 10 }}>
              <Metric label="기간" value={period(detail.startDate, detail.endDate)} />
              <Metric label="마감까지" value={deadline?.text ?? '—'} color={deadline?.color} />
              <Metric label="데이터 버전 (낙관적 락)" value={`v${detail.version}`} />
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 10 }}>
              <Metric label="계약 M/M" value={detail.contractMm ? `${detail.contractMm} MM` : '—'} />
              <Metric label="수행 형태" value={ENGAGEMENT_LABEL[detail.engagement]} />
              <Metric label="계약 솔루션" value={detail.solution || '—'} />
            </div>

            {showProgress && <div style={{ marginTop: 18 }}><ProgressEditor /></div>}

            <ProjectActions />

            {error && (
              <div style={{ marginTop: 12 }}>
                <ErrorText code={error.code} message={error.message} />
              </div>
            )}

            {confirmDelete && (
              <div className="confirm-card" style={{ marginTop: 12 }}>
                <div className="t">삭제하시겠습니까?</div>
                <div style={{ fontSize: 13, marginBottom: 12 }}>
                  목록·중복 검사에서 빠집니다. 배정·감사 이력은 보존됩니다(소프트 삭제).
                </div>
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                  <button className="btn btn-ghost" style={{ padding: '7px 14px', fontSize: 12.5 }}
                    onClick={() => setConfirmDelete(false)}>취소</button>
                  <button className="btn btn-danger" style={{ padding: '7px 16px', fontSize: 12.5 }}
                    disabled={busy} onClick={() => void runDelete()}>삭제</button>
                </div>
              </div>
            )}
          </section>
        </div>

        <aside style={{ display: 'grid', gap: 16 }}>
          <AssignmentPanel editable={canAssign(me, detail)} />
        </aside>
      </div>

      {editing && <ProjectEditModal onClose={() => setEditing(false)} />}
    </div>
  )
}
