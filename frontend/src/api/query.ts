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
  /** 目标数据库（可选），后端执行前 USE */
  database?: string
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

/** 切换默认数据库（USE 语句；标识符反引号转义） */
export function useDatabase(connectionId: string, schema: string): Promise<QueryExecuteResponse> {
  const quoted = `\`${schema.replace(/`/g, '``')}\``
  return request<QueryExecuteResponse>(
    'POST',
    `/api/v1/connections/${encodeURIComponent(connectionId)}/queries`,
    { sql: `USE ${quoted}`, maxRows: 1, queryTimeoutSec: 10 },
  )
}
