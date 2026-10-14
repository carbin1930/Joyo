import { ipcMain } from 'electron'
import type { ConnectionsStore, SaveConnectionInput } from './secure-store'

/**
 * 连接管理 IPC 通道注册（通道白名单见 docs/30-api/rest-api.md 连接管理节）。
 * 载荷契约：通道名 `域:动作`；输入/输出类型与 frontend/src/types/ipc.d.ts 保持一致。
 */
export function registerConnectionsIpc(store: ConnectionsStore): void {
  ipcMain.handle('connections:list', () => {
    return store.list()
  })

  ipcMain.handle('connections:save', (_event, input: SaveConnectionInput) => {
    return store.save(input)
  })

  ipcMain.handle('connections:delete', (_event, id: string) => {
    return store.remove(id)
  })

  ipcMain.handle('secure:decrypt', (_event, id: string) => {
    return store.decryptPassword(id)
  })
}
