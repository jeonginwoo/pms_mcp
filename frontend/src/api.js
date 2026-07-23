/*
 * PMS 백엔드 API 클라이언트.
 * 로그인(이메일+비밀번호) 토큰을 보관하고 모든 요청에 Bearer로 싣는다.
 * 서버 DTO → 화면 모델 필드 매핑(startDate→start, managerId→pmId 등)도 여기서 처리.
 */
const TOKEN_KEY = 'pms.token'

export function getToken() { return localStorage.getItem(TOKEN_KEY) }
export function clearToken() { localStorage.removeItem(TOKEN_KEY) }

/** 로그인 — 계정 ID(이메일) + 비밀번호 (초기 비밀번호 proten1!) */
export async function login(email, password) {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  const body = await res.json().catch(() => null)
  if (!res.ok) throw new Error((body && body.error) || '로그인에 실패했습니다')
  localStorage.setItem(TOKEN_KEY, body.token)
  return body
}

async function request(path, options = {}) {
  const res = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getToken() || ''}`,
      ...(options.headers || {}),
    },
  })
  const text = await res.text()
  let body = null
  try { body = text ? JSON.parse(text) : null } catch { body = null }
  if (!res.ok) {
    const message = (body && (body.error || body.message)) || `요청 실패 (${res.status})`
    const err = new Error(message)
    err.status = res.status
    throw err
  }
  return body
}

// ── 화면 모델 매핑 ──
const mapProject = (p) => ({
  id: p.id, name: p.name, client: p.client, status: p.status,
  start: p.startDate, end: p.endDate, progress: p.progress, version: p.version,
  team: p.team, pmId: p.managerId,
  contractMm: p.contractMm, engagement: p.engagement, solution: p.solution,
})
const mapNotif = (n) => ({
  id: n.id, msg: n.message, unread: !n.read,
  at: (n.createdAt || '').replace('T', ' ').slice(0, 16),
})

export const api = {
  health: () => request('/api/health'),
  me: () => request('/api/me'),
  people: () => request('/api/people'),
  projects: async () => (await request('/api/projects')).map(mapProject),
  projectDetail: async (id) => { const d = await request(`/api/projects/${id}`); return { ...mapProject({ ...d, managerId: d.managerId }), division: d.division, assignments: d.assignments } },
  assignments: () => request('/api/projects/assignments'),
  maintenance: (contractId) => request(`/api/maintenance/${contractId}`),
  audit: () => request('/api/audit'),
  notifications: async () => { const d = await request('/api/notifications'); return { unread: d.unread, items: (d.items || []).map(mapNotif) } },
  markNotificationRead: (id) => request(`/api/notifications/${id}/read`, { method: 'POST' }),

  updateProgress: (id, { percent, version, confirmed }) =>
    request(`/api/projects/${id}/progress`, { method: 'PUT', body: JSON.stringify({ percent, version, confirmed }) }),
  createProject: async (form) => mapProject(await request('/api/projects', { method: 'POST', body: JSON.stringify(form) })),
  updateProject: async (id, form) => mapProject(await request(`/api/projects/${id}`, { method: 'PUT', body: JSON.stringify(form) })),
  deleteProject: (id) => request(`/api/projects/${id}`, { method: 'DELETE' }),

  upsertAssignment: (projectId, { personId, month, mm }) =>
    request(`/api/projects/${projectId}/assignments`, { method: 'PUT', body: JSON.stringify({ personId, month, mm }) }),
  removeAssignment: (projectId, personId, month) =>
    request(`/api/projects/${projectId}/assignments/${personId}/${month}`, { method: 'DELETE' }),

  createPerson: (form) => request('/api/people', { method: 'POST', body: JSON.stringify(form) }),
  updatePerson: (id, form) => request(`/api/people/${id}`, { method: 'PUT', body: JSON.stringify(form) }),
  deletePerson: (id) => request(`/api/people/${id}`, { method: 'DELETE' }),

  // 내 계정
  account: () => request('/api/me/account'),
  updateProfile: (form) => request('/api/me/profile', { method: 'PUT', body: JSON.stringify(form) }),
  changePassword: (current, newPassword) =>
    request('/api/me/password', { method: 'PUT', body: JSON.stringify({ current, newPassword }) }),
  updateNotifPrefs: (prefs) => request('/api/me/notif-prefs', { method: 'PUT', body: JSON.stringify(prefs) }),

  chat: (conversationId, message) =>
    request('/api/chat', { method: 'POST', body: JSON.stringify({ conversationId, message }) }),
  chatFeedback: (conversationId, rating, reason) =>
    request('/api/chat/feedback', { method: 'POST', body: JSON.stringify({ conversationId, rating, reason }) }),
}

/** SSE 알림 스트림 URL — EventSource는 헤더를 못 실어 access_token 쿼리 파라미터 사용 */
export function notificationStreamUrl() {
  return `/api/notifications/stream?access_token=${encodeURIComponent(getToken() || '')}`
}
