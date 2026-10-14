import type { ApiResponse } from '@/types/api'
import type { JoyoLaunchConfig } from '@/types/ipc'

/** 业务错误：code != 0（docs/90-conventions/api-conventions.md） */
export class ApiError extends Error {
  constructor(
    public readonly code: number,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/** 后端不可达 / 未就绪：App 级监听并显示异常遮罩 */
export class BackendUnavailableError extends Error {
  constructor(message = '后端服务异常') {
    super(message)
    this.name = 'BackendUnavailableError'
  }
}

export interface ApiConfig {
  baseUrl: string
  token: string
}

let configPromise: Promise<ApiConfig> | null = null

/**
 * 解析后端连接配置：优先使用 Electron Preload 注入的启动参数；
 * 不存在（纯浏览器开发模式）时降级为本地开发默认值。
 * 用 globalThis 取 window：兼容 vitest node 环境与 Electron 渲染进程。
 */
export function getApiConfig(): Promise<ApiConfig> {
  if (!configPromise) {
    const injected = (globalThis as { joyo?: { getLaunchConfig(): Promise<JoyoLaunchConfig> } }).joyo
    configPromise = injected
      ? injected.getLaunchConfig()
      : Promise.resolve({ baseUrl: 'http://127.0.0.1:18080', token: 'dev-token' })
  }
  return configPromise
}

/** 测试专用：重置已缓存的配置 */
export function resetApiConfig(): void {
  configPromise = null
}

/**
 * 唯一的请求出口（docs/90-conventions/coding-frontend.md 第 3 节）：
 * - 自动注入 X-Joyo-Token
 * - code === 0 返回 data；非 0 抛 ApiError
 * - 网络失败 / 非 200 抛 BackendUnavailableError
 */
export async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const config = await getApiConfig()

  let resp: Response
  try {
    resp = await fetch(config.baseUrl + path, {
      method,
      headers: {
        'Content-Type': 'application/json;charset=UTF-8',
        'X-Joyo-Token': config.token,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new BackendUnavailableError()
  }

  if (!resp.ok) {
    throw new BackendUnavailableError(`后端返回异常状态: ${resp.status}`)
  }

  const payload = (await resp.json()) as ApiResponse<T>
  if (payload.code !== 0) {
    throw new ApiError(payload.code, payload.message)
  }
  return payload.data
}
