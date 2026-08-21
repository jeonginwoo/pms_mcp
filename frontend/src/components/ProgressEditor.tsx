/*
 * 진척률 편집 (AC A2-1~A2-3 · 2026-08-09 결정 ①).
 *
 * 웹 UI는 **100% 저장만 확인 모달**을 띄우고 그 외 값은 1클릭으로 저장한다 — 서비스
 * 프로토콜(2단계 confirmed)과 MCP 확인 카드는 그대로이고, 확인 강도만 입구별로 다르다.
 * 100%가 특별한 이유: 그 저장이 완료 처리(A7)의 전제를 만든다.
 *
 * 수정은 **진행중 상태에서만** 열린다(2026-08-22 결정) — 계약대기·수주확정에는 기록할
 * 진척이 없고 완료는 재개가 먼저다. 서버도 같은 규칙으로 409를 돌려준다.
 */
import { useEffect, useState } from 'react'
import { useStore } from '../store'
import { STATUS_LABEL } from '../labels'
import { ErrorText } from './ui'
import type { ProgressUpdateResult } from '../types/api'

export default function ProgressEditor() {
  const { detail, saveProgress, showToast } = useStore()
  const [value, setValue] = useState(detail?.progress ?? 0)
  const [summary, setSummary] = useState<ProgressUpdateResult | null>(null)
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)

  // 상세가 갈아치워지면(저장·재개 등) 편집값도 서버 값으로 맞춘다
  useEffect(() => {
    setValue(detail?.progress ?? 0)
    setSummary(null)
  }, [detail?.id, detail?.progress, detail?.version])

  if (!detail) {
    return null
  }

  const delta = value - detail.progress
  const completed = detail.status === 'COMPLETED'
  // 진척률은 진행중에서만 수정한다 (2026-08-22 결정 — 서버도 409로 막는다)
  const editable = detail.status === 'IN_PROGRESS'

  const commit = async (progress: number) => {
    setBusy(true)
    setError(null)
    const result = await saveProgress(progress, true)
    setBusy(false)
    setSummary(null)

    if (result.ok) {
      showToast(result.value.completable
        ? '저장되었습니다 — 이제 완료 처리할 수 있습니다'
        : '저장되었습니다')

      return
    }

    setError({ code: result.error.code, message: result.error.message })
  }

  /** 100%는 서버 요약을 받아 확인 모달로, 그 외는 바로 저장 (2026-08-09 ①). */
  const save = async () => {
    if (value < 100) {
      await commit(value)

      return
    }

    setBusy(true)
    setError(null)
    const preview = await saveProgress(value, false)
    setBusy(false)

    if (preview.ok) {
      setSummary(preview.value)

      return
    }

    setError({ code: preview.error.code, message: preview.error.message })
  }

  return (
    <div style={{ borderTop: '1px solid var(--border-soft)', paddingTop: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 6 }}>
        <span className="muted" style={{ fontSize: 12, fontWeight: 600 }}>진행률</span>
        <span style={{ fontSize: 15, fontWeight: 800 }}>{detail.progress}%</span>
      </div>

      <div style={{ position: 'relative', background: 'var(--chip)', borderRadius: 12, height: 22, marginBottom: 14 }}>
        <div style={{ position: 'absolute', inset: 0, borderRadius: 12, overflow: 'hidden' }}>
          <div style={{ position: 'absolute', inset: '0 auto 0 0', width: `${detail.progress}%`, background: 'linear-gradient(90deg,#3d63d8,#6f96ff)' }} />
          <div style={{
            position: 'absolute',
            top: 0,
            bottom: 0,
            left: `${Math.min(detail.progress, value)}%`,
            width: `${Math.abs(delta)}%`,
            background: delta >= 0 ? 'rgba(61,99,216,.30)' : 'rgba(216,58,58,.28)',
          }} />
        </div>
        <div style={{ position: 'absolute', top: -3, bottom: -3, left: `${value}%`, width: 2, background: 'var(--text)', borderRadius: 2, opacity: delta === 0 ? 0 : .8, marginLeft: -1 }} />
        <input type="range" min="0" max="100" value={value} disabled={!editable}
          onChange={(e) => { setValue(Number(e.target.value)); setSummary(null) }}
          style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', opacity: 0, margin: 0, cursor: editable ? 'ew-resize' : 'default' }} />
      </div>

      {!editable ? (
        <div className="muted2" style={{ fontSize: 12.5 }}>
          {completed
            ? '완료된 프로젝트의 진척률은 수정할 수 없습니다 — 재개 후 수정하세요 (409 PROJECT_COMPLETED).'
            : `진척률은 진행중 상태에서만 수정할 수 있습니다 (현재 ${STATUS_LABEL[detail.status]}).`}
        </div>
      ) : (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <input type="number" min="0" max="100" value={value}
              onChange={(e) => {
                setValue(Math.max(0, Math.min(100, Number(e.target.value) || 0)))
                setSummary(null)
              }}
              style={{ width: 64, fontWeight: 700, fontSize: 14, textAlign: 'right', padding: '7px 8px' }} />
            <span className="muted" style={{ fontSize: 13, fontWeight: 700 }}>%</span>
            <span style={{ fontSize: 12.5, fontWeight: 700, color: delta === 0 ? 'var(--muted2)' : delta > 0 ? 'var(--ok)' : 'var(--danger)' }}>
              {delta === 0 ? '변경 없음' : `${delta > 0 ? '+' : ''}${delta}%p`}
            </span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            {[25, 50, 75, 100].map((preset) => (
              <button key={preset} className="chip-btn" onClick={() => { setValue(preset); setSummary(null) }}>
                {preset}%
              </button>
            ))}
            <button className="btn btn-primary" disabled={delta === 0 || busy}
              style={{ opacity: delta === 0 ? .45 : 1 }} onClick={() => void save()}>
              {value === 100 ? '100% 저장 검토 →' : '저장'}
            </button>
          </div>
        </div>
      )}

      {summary && (
        <div className="confirm-card" style={{ marginTop: 12 }}>
          <div className="t">확인 필요 — 아직 저장되지 않았습니다</div>
          <div style={{ fontSize: 13, marginBottom: 12 }}>
            {summary.name} 진척률 {summary.currentProgress}% → {summary.requestedProgress}%
            (v{summary.version} 기준)
            {summary.completable && ' · 저장 후 완료 처리가 가능해집니다'}
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <button className="btn btn-ghost" style={{ padding: '7px 14px', fontSize: 12.5 }}
              onClick={() => setSummary(null)}>취소</button>
            <button className="btn btn-primary" style={{ padding: '7px 16px', fontSize: 12.5 }}
              disabled={busy} onClick={() => void commit(summary.requestedProgress)}>
              확인하고 저장
            </button>
          </div>
        </div>
      )}

      {error && (
        <div style={{ marginTop: 12 }}>
          <ErrorText code={error.code} message={error.message} />
        </div>
      )}
    </div>
  )
}
