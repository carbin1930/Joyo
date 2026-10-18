import { request } from './http'
import type { HealthData } from '@/types/api'

/** 系统类接口（docs/30-api/rest-api.md 第 1 节） */
export function getHealth(): Promise<HealthData> {
  return request<HealthData>('GET', '/api/v1/health')
}

export interface LogTailResponse {
  path: string
  lines: string[]
  truncated: boolean
}

/** 后端日志尾部（F13 精简版） */
export function getLogs(tail = 200): Promise<LogTailResponse> {
  return request<LogTailResponse>('GET', `/api/v1/logs?tail=${tail}`)
}
