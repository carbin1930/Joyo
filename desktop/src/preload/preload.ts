import { contextBridge, ipcRenderer } from 'electron'
import type { MetricsTick } from '../main/metrics-sampler'
import type { ExportJob } from '../main/export-runner'
import type { AppSettings } from '../main/settings-store'
import type { UpdateState } from '../main/updater'

/**
 * Preload：仅通过 contextBridge 暴露白名单 API（overview.md 第 4 节安全模型）。
 * 渲染进程类型定义见 frontend/src/types/ipc.d.ts，两侧签名必须一致。
 */

let cached: Promise<{ baseUrl: string; token: string }> | null = null

function getLaunchConfig(): Promise<{ baseUrl: string; token: string }> {
  cached ??= ipcRenderer.invoke('joyo:get-launch-config')
  return cached
}

contextBridge.exposeInMainWorld('joyo', {
  getLaunchConfig,
  /** 菜单"打开后端日志" → 通知渲染进程打开日志弹窗 */
  onOpenLogs: (cb: () => void): void => {
    ipcRenderer.on('joyo:open-logs', () => cb())
  },
  /** spec-13：在系统文件管理器中定位文件（日志"打开目录"按钮） */
  showItemInFolder: (target: string): Promise<void> => ipcRenderer.invoke('shell:show-item', target),
  /** spec-13：写系统剪贴板（日志"复制全部"按钮） */
  copyText: (text: string): Promise<void> => ipcRenderer.invoke('clipboard:write-text', text),
  /** spec-14：订阅资源监控推送（Main 每 3s 一帧），返回取消订阅函数 */
  onMetrics: (cb: (tick: MetricsTick) => void): (() => void) => {
    const listener = (_event: unknown, tick: MetricsTick): void => cb(tick)
    ipcRenderer.on('metrics:tick', listener)
    return () => ipcRenderer.removeListener('metrics:tick', listener)
  },
  /** spec-18：读取全局设置（持久化在 Main） */
  getSettings: (): Promise<AppSettings> => ipcRenderer.invoke('settings:get'),
  /** spec-18：合并保存设置（部分更新），返回保存后的完整设置 */
  setSettings: (patch: Partial<AppSettings>): Promise<AppSettings> =>
    ipcRenderer.invoke('settings:set', patch),
  /** spec-19：检查更新，返回当前更新状态 */
  checkUpdate: (): Promise<UpdateState> => ipcRenderer.invoke('updater:check'),
  /** spec-19：退出并安装已下载的新版本 */
  installUpdate: (): Promise<void> => ipcRenderer.invoke('updater:install'),
  /** spec-19：订阅更新状态推送（检查/下载进度/完成/失败），返回取消订阅函数 */
  onUpdateEvent: (cb: (state: UpdateState) => void): (() => void) => {
    const listener = (_event: unknown, state: UpdateState): void => cb(state)
    ipcRenderer.on('updater:event', listener)
    return () => ipcRenderer.removeListener('updater:event', listener)
  },
  /** spec-15：导出（保存对话框 → Main 拉流写盘）；取消返回 { canceled: true } */
  exportRun: (
    job: ExportJob & { defaultFileName: string },
  ): Promise<{ canceled: true } | { canceled: false; filePath: string; bytes: number }> =>
    ipcRenderer.invoke('export:run', job),
  /** 查询历史有新增（spec-05）：返回取消订阅函数 */
  onHistoryUpdated: (cb: () => void): (() => void) => {
    const listener = () => cb()
    ipcRenderer.on('history:updated', listener)
    return () => ipcRenderer.removeListener('history:updated', listener)
  },
  connections: {
    list: () => ipcRenderer.invoke('connections:list'),
    save: (input: unknown) => ipcRenderer.invoke('connections:save', input),
    remove: (id: string) => ipcRenderer.invoke('connections:delete', id),
    /** field: 'password'(默认) | 'sshPassword' | 'sshPassphrase'（spec-06） */
    decryptPassword: (id: string, field?: string) => ipcRenderer.invoke('secure:decrypt', id, field ?? 'password'),
    /** spec-06：私钥文件选择器，返回绝对路径或 null */
    pickPrivateKey: () => ipcRenderer.invoke('dialog:pick-private-key'),
  },
  history: {
    append: (entry: unknown) => ipcRenderer.invoke('history:append', entry),
    list: (query?: unknown) => ipcRenderer.invoke('history:list', query),
    remove: (id: number) => ipcRenderer.invoke('history:delete', id),
    clear: () => ipcRenderer.invoke('history:clear'),
  },
})
