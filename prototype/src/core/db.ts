// 시드(reference/seed) → 프로토타입 초기 데이터 구성
// 시드에 없는 값(월별 M/M·billable·유지보수)은 부록 B 적재 정책의 "화면 검증용 가정"으로 채운다.
// 실제 적재 규칙은 PMS-M1 전 결정(12장) — 여기 가정은 정책 결정의 참고 자료.
import peopleSeed from '../data/people.json'
import projectsSeed from '../data/projects.json'
import type { Person, Project, Assignment, Grade, OrgUnit, RoleGroup } from '../types'

// billable 가정: 대표·영업/기획/경영 지원조직 false (상위 PRD §3 — 팀 목록은 시드 정책에서 확정)
const NON_BILLABLE_TEAMS = new Set([
  '프로텐', 'AX사업기획부', 'AX영업팀', 'AX기획마케팅팀', '관리•마케팅부', '경영관리팀',
])

export function buildPeople(): Person[] {
  const people: Person[] = (peopleSeed as any[]).map((p) => ({
    ...p,
    billable: !NON_BILLABLE_TEAMS.has(p.team),
    active: true,
    phone: '',
    notifPrefs: { progress: true, project: true, org: true, weekly: true },
  }))
  // 회사 고유 ADMIN 계정 — 특정 인물이 아닌 삭제 불가 시스템 계정 (피드백 #6, 기획 결정 후보)
  people.push({
    id: 100, name: '시스템 관리자', grade: '-', email: 'admin@proten.co.kr',
    team: '프로텐', division: '프로텐', orgRole: 'ADMIN', gradeCoeff: 1.0,
    billable: false, active: true, isSystem: true, phone: '',
    notifPrefs: { progress: true, project: true, org: true, weekly: true },
  })
  return people
}

// 조직 트리 — 시드의 division/team 2단을 회사(root) 아래 트리로 구성. 이후 화면에서 임의 깊이 확장
export function buildOrgUnits(people: Person[]): OrgUnit[] {
  const units: OrgUnit[] = [{ id: 1, name: '프로텐', parentId: null }]
  let id = 1
  const byName = new Map<string, number>([['프로텐', 1]])
  for (const p of people) {
    if (p.isSystem) continue
    if (!byName.has(p.division)) {
      byName.set(p.division, ++id)
      units.push({ id, name: p.division, parentId: 1 })
    }
    if (p.team !== p.division && !byName.has(p.team)) {
      byName.set(p.team, ++id)
      units.push({ id, name: p.team, parentId: byName.get(p.division)! })
    }
  }
  return units
}

// 권한 그룹 기본 4종 — 시드 orgRole과 정합. 관리자 그룹은 시스템 고정(자기 잠금 방지)
export function buildRoleGroups(): RoleGroup[] {
  return [
    { key: 'ADMIN', name: '관리자', scope: 'ALL', createProject: true, manageContract: true, manageOrg: true, adminAll: true, system: true },
    { key: 'DIVISION_HEAD', name: '부문장', scope: 'DIVISION', createProject: true, manageContract: true, manageOrg: false, adminAll: false },
    { key: 'TEAM_LEAD', name: '팀장', scope: 'TEAM', createProject: true, manageContract: true, manageOrg: false, adminAll: false },
    { key: 'MEMBER', name: '팀원', scope: 'SELF', createProject: false, manageContract: false, manageOrg: false, adminAll: false },
  ]
}

export function buildGrades(people: Person[]): Grade[] {
  const map = new Map<string, number>()
  for (const p of people) if (!p.isSystem) map.set(p.grade, p.gradeCoeff)
  return [...map.entries()].map(([name, coeff]) => ({ name, coeff }))
}

export function buildProjects(): Project[] {
  return (projectsSeed as any[]).map((p, i) => ({
    id: i + 1,
    ...p,
    // 수행형태 3종 확정(피드백 2차 #1) — 시드의 OFFSITE 32건은 원격으로 흡수. 실제 적재 시 변환 규칙 확정 필요
    engagement: p.engagement === 'OFFSITE' ? 'REMOTE' : p.engagement,
    deleted: false,
    version: 1,
  }))
}

// 배정: managerId → PM, 나머지 assigneeIds → PARTICIPANT (부록 B 확정 규칙)
// 월별 M/M은 시드 공백 — 결정적 의사난수로 부여(가동률 화면 검증용)
export function buildAssignments(projects: Project[]): Assignment[] {
  const rows: Assignment[] = []
  let id = 1
  const MM = [0.2, 0.3, 0.5, 0.5, 0.7, 1.0]
  for (const p of projects) {
    const ids = new Set<number>([p.managerId, ...((p as any).assigneeIds ?? [])])
    for (const personId of ids) {
      rows.push({
        id: id++,
        projectId: p.id,
        personId,
        role: personId === p.managerId ? 'PM' : 'PARTICIPANT',
        startDate: p.startDate,
        endDate: p.endDate,
        monthlyMM: MM[(p.id * 7 + personId * 13) % MM.length],
        status: 'ACTIVE',
      })
    }
  }
  return rows
}
