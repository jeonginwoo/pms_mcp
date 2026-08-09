// 가시성 — 상위 PRD §4-4. 권한 그룹 scope(전사/부문/팀/본인) ∪ 내가 배정된 프로젝트. 범위 밖 = 404 은닉.
// 팀 scope는 조직 트리의 하위 조직을 포함한다(피드백 2차 #3 — 트리 구조).
import type { Assignment, OrgUnit, Person, Project, RoleGroup } from '../types'
import { groupOf } from './permissions'

interface Ctx { roleGroups: RoleGroup[]; orgUnits: OrgUnit[] }

/** unitName 기준 하위 조직(자기 포함) 이름 집합 */
export function subtreeNames(orgUnits: OrgUnit[], unitName: string): Set<string> {
  const root = orgUnits.find((u) => u.name === unitName)
  if (!root) return new Set([unitName])
  const names = new Set<string>()
  const walk = (id: number, name: string) => {
    names.add(name)
    orgUnits.filter((u) => u.parentId === id).forEach((u) => walk(u.id, u.name))
  }
  walk(root.id, root.name)
  return names
}

/** 조직 단위의 부문(회사 바로 아래 조상) 이름 — 트리 개편 시 denormalized division 재계산에 사용 */
export function divisionOfUnit(orgUnits: OrgUnit[], unitName: string): string {
  let u = orgUnits.find((x) => x.name === unitName)
  if (!u) return unitName
  while (u.parentId !== null) {
    const parent = orgUnits.find((x) => x.id === u!.parentId)!
    if (parent.parentId === null) return u.name // 회사 바로 아래 = 부문
    u = parent
  }
  return u.name // root 직속(회사 소속)
}

export function orgScopePeople(viewer: Person, people: Person[], ctx: Ctx): Person[] {
  switch (groupOf(viewer, ctx.roleGroups).scope) {
    case 'ALL': return people
    case 'DIVISION': return people.filter((p) => p.division === viewer.division)
    case 'TEAM': {
      const names = subtreeNames(ctx.orgUnits, viewer.team)
      return people.filter((p) => names.has(p.team))
    }
    default: return people.filter((p) => p.id === viewer.id)
  }
}

export function isProjectVisible(
  viewer: Person, project: Project, assignments: Assignment[], ctx: Ctx,
): boolean {
  if (project.deleted) return false
  const scope = groupOf(viewer, ctx.roleGroups).scope
  if (scope === 'ALL') return true
  const assigned = assignments.some(
    (a) => a.projectId === project.id && a.personId === viewer.id && a.status === 'ACTIVE',
  )
  if (assigned) return true
  if (scope === 'DIVISION') return project.division === viewer.division
  if (scope === 'TEAM') return subtreeNames(ctx.orgUnits, viewer.team).has(project.team)
  return false // SELF: 본인 참여만
}

export function visibleProjects(
  viewer: Person, projects: Project[], assignments: Assignment[], ctx: Ctx,
): Project[] {
  return projects.filter((p) => isProjectVisible(viewer, p, assignments, ctx))
}
