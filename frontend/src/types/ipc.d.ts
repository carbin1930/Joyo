// Electron Preload 注入的全局对象类型（docs/90-conventions/coding-frontend.md 第 5 节）
// desktop 骨架完成前，开发模式下降级为开发默认配置（见 api/http.ts）

export interface JoyoLaunchConfig {
  /** 后端地址，如 http://127.0.0.1:18080 */
  baseUrl: string
  /** Electron Main 每次启动下发的随机 Token */
  token: string
}

declare global {
  interface Window {
    joyo?: {
      getLaunchConfig(): Promise<JoyoLaunchConfig>
    }
  }
}

export {}
