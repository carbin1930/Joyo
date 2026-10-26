import { request } from './http'
import type { SyncCompareRequest, SyncCompareResponse, SyncExecuteResponse } from '@/types/api'

/**
 * 表/数据同步接口（spec-17，rest-api.md 第 4 节）。
 * execute 与 compare 入参相同：服务端重新比对后执行，不接受客户端 SQL。
 */

export function compareTables(payload: SyncCompareRequest): Promise<SyncCompareResponse> {
  return request<SyncCompareResponse>('POST', '/api/v1/sync/compare', payload)
}

export function executeSync(payload: SyncCompareRequest): Promise<SyncExecuteResponse> {
  return request<SyncExecuteResponse>('POST', '/api/v1/sync/execute', payload)
}
