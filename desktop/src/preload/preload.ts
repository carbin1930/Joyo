import { contextBridge, ipcRenderer } from 'electron'

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
