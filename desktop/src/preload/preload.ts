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
  connections: {
    list: () => ipcRenderer.invoke('connections:list'),
    save: (input: unknown) => ipcRenderer.invoke('connections:save', input),
    remove: (id: string) => ipcRenderer.invoke('connections:delete', id),
    decryptPassword: (id: string) => ipcRenderer.invoke('secure:decrypt', id),
  },
})
