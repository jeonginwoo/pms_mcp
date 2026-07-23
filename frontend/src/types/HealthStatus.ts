/** 서버 DTO HealthStatus와 이름·필드를 일치시킨다 (Ubiquitous Language) */
export interface HealthStatus {
  status: 'UP' | 'DOWN'
  database: boolean
}
