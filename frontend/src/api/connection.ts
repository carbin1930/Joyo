import { request } from './http'

/** 连接表单数据（用于测试连接与建立会话；后端字段见 rest-api.md 连接管理节） */
export interface ConnectionFormPayload {
  host: string
  port: number
  username: string
  password: string
  defaultDatabase?: string
}

export interface ConnectionTestResult {
  serverVersion: string
  elapsedMs: number
}

export interface SessionInfo {
  connectionId: string
  serverVersion: string
  defaultDatabase: string
}

/** 用表单当前值直接测试连接（无需先保存，spec-01 行为细节 2） */
export function testConnection(payload: ConnectionFormPayload): Promise<ConnectionTestResult> {
  return request<ConnectionTestResult>('POST', '/api/v1/connections/test', payload)
}

/** 建立会话：后端按参数创建 HikariDataSource 并缓存 */
export function openSession(
  connectionId: string,
  payload: ConnectionFormPayload,
): Promise<SessionInfo> {
  return request<SessionInfo>(
    'POST',
    `/api/v1/connections/${encodeURIComponent(connectionId)}/session`,
    payload,
  )
}

/** 断开会话（幂等） */
export function closeSession(connectionId: string): Promise<null> {
  return request<null>('DELETE', `/api/v1/connections/${encodeURIComponent(connectionId)}/session`)
}
