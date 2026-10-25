import { BrowserWindow, ipcMain } from 'electron'
import type { HistoryEntryInput, HistoryListQuery, HistoryStore } from './history-store'

/**
 * 查询历史 IPC 通道注册（spec-05；通道白名单见 docs/30-api/rest-api.md 连接管理节）。
 * 载荷契约：输入/输出类型与 frontend/src/types/ipc.d.ts 保持一致。
 * 事件：history:updated 在 append 后广播，打开中的历史面板据此实时刷新。
 */
export function registerHistoryIpc(store: HistoryStore): void {
  ipcMain.handle('history:append', (_event, entry: HistoryEntryInput) => {
    store.append(entry)
    for (const win of BrowserWindow.getAllWindows()) {
      win.webContents.send('history:updated')
    }
  })

  ipcMain.handle('history:list', (_event, query: HistoryListQuery | undefined) => {
    return store.list(query ?? {})
  })

  ipcMain.handle('history:delete', (_event, id: number) => {
    return store.remove(id)
  })

  ipcMain.handle('history:clear', () => {
    store.clear()
  })
}
