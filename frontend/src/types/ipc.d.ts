// Electron Preload 注入的全局对象类型（docs/90-conventions/coding-frontend.md 第 5 节）
// 与 desktop/src/preload/preload.ts 的 contextBridge 暴露签名一一对应；
// 载荷契约见 docs/30-api/rest-api.md 连接管理节（IPC 白名单）。

import type { MetricsTick } from './api'

/** 全局设置（spec-18，与 desktop/src/main/settings-store.ts 的 AppSettings 一致） */
export interface AppSettings {
  theme: 'light' | 'dark'
  fontSize: number
  maxRows: number
  queryTimeoutSec: number
}

/** 更新状态（spec-19，与 desktop/src/main/updater.ts 的 UpdateState 一致） */
export interface UpdateState {
  phase: 'idle' | 'checking' | 'available' | 'not-available' | 'downloading' | 'downloaded' | 'error' | 'unsupported'
  version?: string
  notes?: string
  percent?: number
  message?: string
}

/** 导出任务（spec-15，与 desktop/src/main/export-runner.ts 的 ExportJob 一致） */
export interface ExportJob {
  connectionId: string
  sql: string
  format: 'csv' | 'insert' | 'xlsx'
  database?: string
  tableName?: string
  rowLimit?: number
}

export type ExportResult = { canceled: true } | { canceled: false; filePath: string; bytes: number }

export interface JoyoLaunchConfig {
  /** 后端地址，如 http://127.0.0.1:18080 */
  baseUrl: string
  /** Electron Main 每次启动下发的随机 Token */
  token: string
}

/** SSH 认证方式（spec-06） */
export type SshAuthType = 'password' | 'privateKey'

/** SSH 隧道脱敏视图（spec-06：密文不外传，只有 has 标记） */
export interface SshTunnelView {
  enabled: boolean
  host: string
  port: number
  username: string
  authType: SshAuthType
  privateKeyPath: string
  hasSshPassword: boolean
  hasSshPassphrase: boolean
}

/** SSH 隧道保存入参（明文经 IPC 进 Main 加密落盘） */
export interface SshTunnelInput {
  enabled: boolean
  host: string
  port: number
  username: string
  authType: SshAuthType
  /** 明文；undefined = 保留原值（编辑留空 = 不修改） */
  password?: string
  privateKeyPath?: string
  /** 明文；undefined = 保留原值 */
  passphrase?: string
}

/** secure:decrypt 支持的字段（spec-06 扩展 SSH 两个 secret） */
export type SecretField = 'password' | 'sshPassword' | 'sshPassphrase'

/** 连接列表项（脱敏视图：无 passwordEnc，有 hasPassword） */
export interface ConnectionListItem {
  id: string
  name: string
  host: string
  port: number
  username: string
  defaultDatabase: string
  sshTunnel: SshTunnelView | null
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
  /** SSH 隧道（spec-06）；undefined = 保留原值，null = 清空 */
  sshTunnel?: SshTunnelInput | null
}

/** 查询历史条目（spec-05）：append 入参（不含 id） */
export interface HistoryEntryInput {
  sql: string
  connectionName: string
  database: string
  /** SELECT/UPDATE/.../MULTI；失败条目为 ERROR */
  kind: string
  elapsedMs: number
  rowCount: number | null
  success: boolean
  errorMessage: string | null
  /** ISO-8601 UTC */
  executedAt: string
}

/** 查询历史条目（history:list 返回） */
export interface HistoryItem extends HistoryEntryInput {
  id: number
}

declare global {
  interface Window {
    joyo?: {
      getLaunchConfig(): Promise<JoyoLaunchConfig>
      /** 菜单"打开后端日志"事件（主进程 → 渲染进程） */
      onOpenLogs(cb: () => void): void
      /** spec-13：系统文件管理器定位文件 */
      showItemInFolder(target: string): Promise<void>
      /** spec-13：写系统剪贴板 */
      copyText(text: string): Promise<void>
      /** spec-14：订阅资源监控推送（Main 每 3s 一帧）；返回取消订阅函数 */
      onMetrics(cb: (tick: MetricsTick) => void): () => void
      /** spec-18：读取全局设置 */
      getSettings(): Promise<AppSettings>
      /** spec-18：合并保存设置（部分更新） */
      setSettings(patch: Partial<AppSettings>): Promise<AppSettings>
      /** spec-19：检查更新 */
      checkUpdate(): Promise<UpdateState>
      /** spec-19：退出并安装新版本 */
      installUpdate(): Promise<void>
      /** spec-19：订阅更新状态推送，返回取消订阅函数 */
      onUpdateEvent(cb: (state: UpdateState) => void): () => void
      /** spec-15：导出（保存对话框 → Main 拉流写盘） */
      exportRun(job: ExportJob & { defaultFileName: string }): Promise<ExportResult>
      /** 查询历史有新增（spec-05）；返回取消订阅函数 */
      onHistoryUpdated(cb: () => void): () => void
      connections: {
        list(): Promise<ConnectionListItem[]>
        save(input: SaveConnectionInput): Promise<ConnectionListItem>
        remove(id: string): Promise<boolean>
        /** field: 'password'(默认) | 'sshPassword' | 'sshPassphrase'（spec-06） */
        decryptPassword(id: string, field?: SecretField): Promise<string | null>
        /** spec-06：私钥文件选择器，返回绝对路径或 null */
        pickPrivateKey(): Promise<string | null>
      }
      /** 查询历史（spec-05）：Electron Main 持久化，不经后端 */
      history: {
        append(entry: HistoryEntryInput): Promise<void>
        list(query?: { keyword?: string; limit?: number }): Promise<HistoryItem[]>
        remove(id: number): Promise<boolean>
        clear(): Promise<void>
      }
    }
  }
}

export {}
