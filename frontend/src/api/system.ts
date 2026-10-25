import { request } from './http'
import type { HealthData } from '@/types/api'

/** 系统类接口（docs/30-api/rest-api.md 第 1 节） */
export function getHealth(): Promise<HealthData> {
  return request<HealthData>('GET', '/api/v1/health')
}

export interface LogTailResponse {
  filePath: string
  sizeBytes: number
  lines: string[]
}

/** 后端日志尾部（spec-13：tail 范围 1-2000，文件不存在时 lines 为空） */
export function getLogs(tail = 200): Promise<LogTailResponse> {
  return request<LogTailResponse>('GET', `/api/v1/logs?tail=${tail}`)
}
