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
  /** JVM 资源指标（spec-14） */
  jvm?: JvmInfo | null
}

/** JVM 资源指标（spec-14）：cpuLoad 首次采样为 null，前端显示"--" */
export interface JvmInfo {
  heapUsedMb: number | null
  heapMaxMb: number | null
  cpuLoad: number | null
}

/** 资源监控推送帧（spec-14，Electron Main 每 3s 一帧） */
export interface MetricsTick {
  backendUp: boolean
  electron: { processCount: number; memMb: number; cpuPercent: number }
  jvm: JvmInfo | null
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

// ===== 查询执行（docs/30-api/rest-api.md 查询执行节，与后端 QueryModels 一一对应） =====

/** 列元信息 */
export interface ColumnMeta {
  name: string
  type: string
  nullable: boolean
}

/** 单条语句结果。SELECT 含 queryId/columns/rows/分页；DML 含 affectedRows；DDL 仅 affectedRows */
export interface StatementResult {
  ordinal: number
  kind: 'SELECT' | 'UPDATE' | 'DDL'
  queryId?: string
  columns?: ColumnMeta[]
  totalKnown?: boolean
  totalRows?: number
  truncated?: boolean
  rows?: string[][]
  page?: number
  pageSize?: number
  affectedRows?: number
  elapsedMs: number
}

export interface QueryExecuteResponse {
  statements: StatementResult[]
  elapsedMs: number
}

export interface RowsPageResponse {
  queryId: string
  rows: string[][]
  page: number
  pageSize: number
  totalKnown: boolean
  totalRows: number
}
