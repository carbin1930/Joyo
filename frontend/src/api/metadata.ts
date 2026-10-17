import { request } from './http'
import type {
  SchemasResponse,
  TableDetailResponse,
  TablesResponse,
} from '@/types/api'

/** 元数据接口（spec-02，rest-api.md 元数据节） */

export function getSchemas(connectionId: string): Promise<SchemasResponse> {
  return request<SchemasResponse>(
    'GET',
    `/api/v1/connections/${encodeURIComponent(connectionId)}/schemas`,
  )
}

export function getTables(connectionId: string, schema: string): Promise<TablesResponse> {
  return request<TablesResponse>(
    'GET',
    `/api/v1/connections/${encodeURIComponent(connectionId)}/schemas/${encodeURIComponent(schema)}/tables`,
  )
}

export function getTableDetail(
  connectionId: string,
  schema: string,
  table: string,
): Promise<TableDetailResponse> {
  return request<TableDetailResponse>(
    'GET',
    `/api/v1/connections/${encodeURIComponent(connectionId)}/schemas/${encodeURIComponent(schema)}/tables/${encodeURIComponent(table)}`,
  )
}
