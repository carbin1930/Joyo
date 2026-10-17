import { request } from './http'
import type {
  QueryExecuteResponse,
  RowsPageResponse,
} from '@/types/api'

/** 查询执行接口（spec-03，rest-api.md 查询执行节） */

export interface QueryExecutePayload {
  sql: string
  maxRows?: number
  queryTimeoutSec?: number
}

export function executeQuery(
  connectionId: string,
  payload: QueryExecutePayload,
): Promise<QueryExecuteResponse> {
  return request<QueryExecuteResponse>(
    'POST',
    `/api/v1/connections/${encodeURIComponent(connectionId)}/queries`,
    payload,
  )
}

export function getQueryRows(
  queryId: string,
  page: number,
  pageSize: number,
): Promise<RowsPageResponse> {
  return request<RowsPageResponse>(
    'GET',
    `/api/v1/queries/${encodeURIComponent(queryId)}/rows?page=${page}&pageSize=${pageSize}`,
  )
}
