// 가동률 — 상위 PRD §3 산식. 기본 = Σ배정MM ÷ 가용 × 100 · 보정 = ÷(가용 × coeff)
// 집계(팀·부문·전사·overbooked)의 모집단은 billable=true (2026-08-06 확정)
import type { Assignment, Person, Project } from '../types'

const CAPACITY = 1.0 // 시드 공백 — 기본 1.0 적재(부록 B)

function monthOverlaps(month: string, start: string, end: string): boolean {
  const mStart = `${month}-01`
  const mEnd = `${month}-31`
  return start <= mEnd && end >= mStart
}

export interface UtilRow {
  person: Person
  totalMM: number
  basic: number // %
  adjusted: number // %
  projects: { project: Project; mm: number }[]
}

export function utilizationFor(
  month: string, people: Person[], projects: Project[], assignments: Assignment[],
): UtilRow[] {
  const activeStatuses = new Set(['진행중', '수주확정'])
  return people.map((person) => {
    const rows = assignments.filter((a) => {
      if (a.personId !== person.id || a.status !== 'ACTIVE') return false
      const p = projects.find((x) => x.id === a.projectId)
      if (!p || p.deleted || !activeStatuses.has(p.status)) return false
      return monthOverlaps(month, a.startDate, a.endDate)
    })
    const detail = rows.map((a) => ({
      project: projects.find((x) => x.id === a.projectId)!,
      mm: a.monthlyMM,
    }))
    const totalMM = rows.reduce((s, a) => s + a.monthlyMM, 0)
    const basic = (totalMM / CAPACITY) * 100
    const adjusted = (totalMM / (CAPACITY * person.gradeCoeff)) * 100
    return { person, totalMM, basic, adjusted, projects: detail }
  })
}

export function isOverbooked(row: UtilRow): boolean {
  return row.adjusted > 100
}
