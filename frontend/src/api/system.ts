import { request } from './http'
import type { HealthData } from '@/types/api'

/** 系统类接口（docs/30-api/rest-api.md 第 1 节） */
export function getHealth(): Promise<HealthData> {
  return request<HealthData>('GET', '/api/v1/health')
}
