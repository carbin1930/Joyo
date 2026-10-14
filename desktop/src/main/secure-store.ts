import fs from 'node:fs'
import path from 'node:path'
import { randomUUID } from 'node:crypto'

/**
 * 连接配置持久化唯一模块（docs/10-architecture/data-model.md）：
 * - 读写只发生在 Electron Main 进程
 * - 密码密文格式 enc:v1:<base64>，加解密经注入的 PasswordCipher（safeStorage 实现）
 * - 原子写（tmp + rename）；损坏文件重命名兜底
 * 本模块不依赖 electron，便于纯 node 单元测试。
 */

export interface ConnectionConfig {
  id: string
  name: string
  host: string
  port: number
  username: string
  /** enc:v1:<base64>；空密码为空字符串 */
  passwordEnc: string
  defaultDatabase: string
  /** v0.2 引入（data-model.md 预留） */
  sshTunnel: null
  createdAt: string
  updatedAt: string
}

export interface ConnectionsFile {
  version: 1
  connections: ConnectionConfig[]
}

/** 列表/返回给渲染进程的脱敏视图：passwordEnc 一律不外传（data-model.md 红线） */
export type ConnectionListItem = Omit<ConnectionConfig, 'passwordEnc'> & {
  hasPassword: boolean
}

/** 保存入参（渲染进程 → Main）；id 缺省 = 新建 */
export interface SaveConnectionInput {
  id?: string
  name: string
  host: string
  port: number
  username: string
  /** 明文密码，可选；编辑时留空表示不修改原密码 */
  password?: string
  defaultDatabase?: string
}

export interface PasswordCipher {
  encrypt(plain: string): string
  /** 解密失败（如换机器后钥匙串不匹配）返回 null */
  decrypt(cipherText: string): string | null
}

export function newConnectionsFile(): ConnectionsFile {
  return { version: 1, connections: [] }
}

function toListItem(c: ConnectionConfig): ConnectionListItem {
  const { passwordEnc, ...rest } = c
  return { ...rest, hasPassword: passwordEnc !== '' }
}

export class ConnectionsStore {
  constructor(
    private readonly filePath: string,
    private readonly cipher: PasswordCipher,
  ) {}

  load(): ConnectionsFile {
    if (!fs.existsSync(this.filePath)) return newConnectionsFile()
    try {
      const raw = JSON.parse(fs.readFileSync(this.filePath, 'utf-8')) as ConnectionsFile
      if (raw.version !== 1 || !Array.isArray(raw.connections)) {
        throw new Error('version 或 connections 字段不符')
      }
      return raw
    } catch (e) {
      // data-model.md 第 3 节：损坏文件重命名保留现场，并新建空文件
      const brokenPath = `${this.filePath}.broken.${Date.now()}`
      fs.renameSync(this.filePath, brokenPath)
      console.warn(`[secure-store] 配置文件损坏，已重命名为 ${brokenPath}: ${String(e)}`)
      const fresh = newConnectionsFile()
      this.persist(fresh)
      return fresh
    }
  }

  /** IPC: connections:list */
  list(): ConnectionListItem[] {
    return this.load().connections.map(toListItem)
  }

  /** IPC: connections:save（新建或更新）；返回脱敏视图 */
  save(input: SaveConnectionInput): ConnectionListItem {
    const file = this.load()
    const now = new Date().toISOString()

    if (input.id) {
      const existing = file.connections.find((c) => c.id === input.id)
      if (!existing) {
        throw new Error(`连接不存在: ${input.id}`)
      }
      existing.name = input.name
      existing.host = input.host
      existing.port = input.port
      existing.username = input.username
      existing.defaultDatabase = input.defaultDatabase ?? existing.defaultDatabase
      if (input.password !== undefined) {
        existing.passwordEnc = this.cipher.encrypt(input.password)
      }
      existing.updatedAt = now
      this.persist(file)
      return toListItem(existing)
    }

    const created: ConnectionConfig = {
      id: randomUUID(),
      name: input.name,
      host: input.host,
      port: input.port,
      username: input.username,
      passwordEnc: this.cipher.encrypt(input.password ?? ''),
      defaultDatabase: input.defaultDatabase ?? '',
      sshTunnel: null,
      createdAt: now,
      updatedAt: now,
    }
    file.connections.push(created)
    this.persist(file)
    return toListItem(created)
  }

  /** IPC: connections:delete；返回是否删除了条目 */
  remove(id: string): boolean {
    const file = this.load()
    const before = file.connections.length
    file.connections = file.connections.filter((c) => c.id !== id)
    if (file.connections.length === before) return false
    this.persist(file)
    return true
  }

  /** IPC: secure:decrypt —— 仅用于编辑表单回填；解密失败返回 null（spec-01 边界情况） */
  decryptPassword(id: string): string | null {
    const conn = this.load().connections.find((c) => c.id === id)
    if (!conn) return null
    if (conn.passwordEnc === '') return ''
    return this.cipher.decrypt(conn.passwordEnc)
  }

  private persist(file: ConnectionsFile): void {
    const tmpPath = `${this.filePath}.tmp`
    fs.writeFileSync(tmpPath, JSON.stringify(file, null, 2), 'utf-8')
    fs.renameSync(tmpPath, this.filePath)
  }
}

/** 便捷构造：userData 目录下的标准配置文件路径 */
export function connectionsFilePath(userDataDir: string): string {
  return path.join(userDataDir, 'connections.json')
}
