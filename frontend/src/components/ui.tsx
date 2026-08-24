/*
 * 화면 공용 UI 조각 — styles.css의 클래스를 그대로 쓴다(디자인 원본은 CSS).
 * 동적인 값(색·너비)만 인라인 스타일로 둔다 (conventions §5).
 */
import type { ReactNode } from 'react'
import { STATUS_COLOR, STATUS_LABEL, ROLE_LABEL } from '../labels'
import type { ProjectRole, ProjectStatus } from '../types/api'

export function StatusBadge({ status, big }: { status: ProjectStatus; big?: boolean }) {
  const [color, background] = STATUS_COLOR[status]

  return (
    <span className="badge" style={{
      color,
      background,
      fontSize: big ? 12 : 11.5,
      padding: big ? '4px 12px' : undefined,
    }}>
      {STATUS_LABEL[status]}
    </span>
  )
}

export function RoleBadge({ role }: { role: ProjectRole }) {
  const emphasis = role === 'PM'

  return (
    <span className="badge" style={{
      color: emphasis ? 'var(--primary)' : 'var(--muted)',
      background: emphasis ? 'var(--primary-soft)' : 'var(--chip)',
      fontSize: 11,
      padding: '1px 8px',
    }}>
      {ROLE_LABEL[role]}
    </span>
  )
}

export function Bar({ value, done, height = 17 }: {
  value: number
  done?: boolean
  height?: number
}) {
  return (
    <div className="bar" style={{ height }}>
      <div className={`bar-fill ${done ? 'done' : ''}`} style={{ width: `${Math.min(value, 100)}%` }} />
      <span className="bar-label" style={{ lineHeight: `${height}px` }}>{value}%</span>
    </div>
  )
}

export function Metric({ label, value, color }: {
  label: string
  value: ReactNode
  color?: string
}) {
  return (
    <div className="metric-box">
      <div className="k">{label}</div>
      <div className="v" style={color ? { color } : undefined}>{value}</div>
    </div>
  )
}

export function Modal({ title, width = 520, children, onClose }: {
  title: string
  width?: number
  children: ReactNode
  onClose: () => void
}) {
  return (
    <div className="overlay" onMouseDown={(e) => { if (e.target === e.currentTarget) { onClose() } }}>
      <div className="modal" style={{ width }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
          <h3>{title}</h3>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>닫기</button>
        </div>
        {children}
      </div>
    </div>
  )
}

/**
 * 모달 아래의 버튼 줄 — 취소는 왼쪽, 확정은 오른쪽.
 * 조각으로 올린 이유는 배치가 아니라 **순서**다: 모달마다 손으로 쓰면 어딘가에서
 * 확정이 왼쪽으로 가고, 그러면 사용자가 취소를 누르려다 저장한다.
 */
export function ModalActions({ children }: { children: ReactNode }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
      {children}
    </div>
  )
}

export function Field({ label, hint, children }: {
  label: string
  hint?: string
  children: ReactNode
}) {
  return (
    <div className="field">
      <label>{label}{hint && <span className="muted2" style={{ fontWeight: 500 }}> · {hint}</span>}</label>
      {children}
    </div>
  )
}

/** 서버 에러 문구 — §7 봉투의 code를 함께 보여 준다(사용자가 그대로 신고할 수 있게). */
export function ErrorText({ code, message }: { code: string; message: string }) {
  return (
    <div className="form-err">
      {message} <span className="code" style={{ marginLeft: 4 }}>{code}</span>
    </div>
  )
}

export function Toast({ message }: { message: string }) {
  return <div className="toast">{message}</div>
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>
}
