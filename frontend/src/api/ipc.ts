import type { ConnectionListItem, SaveConnectionInput, SecretField } from '@/types/ipc'

/**
 * 连接配置 IPC 封装（coding-frontend.md 第 4 节：store 之外的 api 层统一出口）。
 * 仅在 Electron 渲染进程内可用；浏览器开发模式抛出可读错误。
 */
function requireIpc(): NonNullable<Window['joyo']> {
  if (!window.joyo) {
    throw new Error('连接配置功能仅在 Joyo 桌面应用内可用（浏览器开发模式不支持）')
  }
  return window.joyo
}

export const connectionsIpc = {
  list(): Promise<ConnectionListItem[]> {
    return requireIpc().connections.list()
  },
  save(input: SaveConnectionInput): Promise<ConnectionListItem> {
    return requireIpc().connections.save(input)
  },
  remove(id: string): Promise<boolean> {
    return requireIpc().connections.remove(id)
  },
  /** 编辑表单回填 / 打开会话取明文用；null = 连接不存在或解密失败 */
  decryptPassword(id: string, field: SecretField = 'password'): Promise<string | null> {
    return requireIpc().connections.decryptPassword(id, field)
  },
  /** spec-06：私钥文件选择器，返回绝对路径或 null */
  pickPrivateKey(): Promise<string | null> {
    return requireIpc().connections.pickPrivateKey()
  },
}
