// 与后端契约一一对应（docs/30-api/rest-api.md），修改接口模型须先改 rest-api.md

/** 统一响应包装：HTTP 200 + 业务码（api-conventions.md 第 3 节） */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** GET /api/v1/health 响应数据 */
export interface HealthData {
  app: string
  version: string
  uptimeSec: number
}

// ===== 元数据（docs/30-api/rest-api.md 元数据节，与后端 MetadataResponses 一一对应） =====

export interface SchemaItem {
  name: string
  isSystem: boolean
}

export interface SchemasResponse {
  schemas: SchemaItem[]
}

/** type ∈ BASE_TABLE | VIEW */
export interface TableItem {
  name: string
  type: 'BASE_TABLE' | 'VIEW'
  engine: string | null
  comment: string
  rowsEstimate: number | null
}

export interface TablesResponse {
  tables: TableItem[]
}

export interface TableColumn {
  name: string
  dataType: string
  length: number | null
  nullable: boolean
  key: string | null
  defaultValue: string | null
  extra: string | null
  comment: string
  ordinal: number
}

export interface TableIndex {
  name: string
  type: string
  unique: boolean
  columns: string[]
}

export interface TableDetailResponse {
  columns: TableColumn[]
  indexes: TableIndex[]
  ddl: string
}
