// Electron Preload 注入的全局对象类型（docs/90-conventions/coding-frontend.md 第 5 节）
// 与 desktop/src/preload/preload.ts 的 contextBridge 暴露签名一一对应；
// 载荷契约见 docs/30-api/rest-api.md 连接管理节（IPC 白名单）。

export interface JoyoLaunchConfig {
  /** 后端地址，如 http://127.0.0.1:18080 */
  baseUrl: string
  /** Electron Main 每次启动下发的随机 Token */
  token: string
}

/** 连接列表项（脱敏视图：无 passwordEnc，有 hasPassword） */
export interface ConnectionListItem {
  id: string
  name: string
  host: string
  port: number
  username: string
  defaultDatabase: string
  sshTunnel: null
  createdAt: string
  updatedAt: string
  hasPassword: boolean
}

/** connections:save 入参（id 缺省 = 新建；password 留空 = 编辑时不修改原密码） */
export interface SaveConnectionInput {
  id?: string
  name: string
  host: string
  port: number
  username: string
  password?: string
  defaultDatabase?: string
}

declare global {
  interface Window {
    joyo?: {
      getLaunchConfig(): Promise<JoyoLaunchConfig>
      /** 菜单"打开后端日志"事件（主进程 → 渲染进程） */
      onOpenLogs(cb: () => void): void
      connections: {
        list(): Promise<ConnectionListItem[]>
        save(input: SaveConnectionInput): Promise<ConnectionListItem>
        remove(id: string): Promise<boolean>
        decryptPassword(id: string): Promise<string | null>
      }
    }
  }
}

export {}
