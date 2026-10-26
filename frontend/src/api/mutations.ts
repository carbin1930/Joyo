import { request } from './http'
import type { MutationRequest, MutationResponse } from '@/types/api'

/** 表数据编辑接口（spec-16，rest-api.md 第 4 节）：一个请求一个事务 */
export function applyMutations(
  connectionId: string,
  payload: MutationRequest,
): Promise<MutationResponse> {
  return request<MutationResponse>(
    'POST',
    `/api/v1/connections/${encodeURIComponent(connectionId)}/mutations`,
    payload,
  )
}
