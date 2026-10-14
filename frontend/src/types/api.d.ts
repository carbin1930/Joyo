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
