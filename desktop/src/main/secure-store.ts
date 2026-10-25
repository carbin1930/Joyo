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

export type SshAuthType = 'password' | 'privateKey'

/** SSH 隧道存储态（spec-06）：两个 secret 均为 enc:v1: 密文 */
export interface SshTunnelSecret {
  enabled: boolean
  host: string
  port: number
  username: string
  authType: SshAuthType
  /** enc:v1:；空为空字符串 */
  sshPasswordEnc: string
  /** enc:v1:；空为空字符串 */
  sshPassphraseEnc: string
  privateKeyPath: string
}

/** 列表视图的 SSH 脱敏形态：密文不外传，只给 has 标记 */
export type SshTunnelView = Omit<SshTunnelSecret, 'sshPasswordEnc' | 'sshPassphraseEnc'> & {
  hasSshPassword: boolean
  hasSshPassphrase: boolean
}

export interface ConnectionConfig {
  id: string
  name: string
  host: string
  port: number
  username: string
  /** enc:v1:<base64>；空密码为空字符串 */
  passwordEnc: string
  defaultDatabase: string
  /** v0.2 引入（spec-06）：未启用/未配置为 null */
  sshTunnel: SshTunnelSecret | null
  createdAt: string
  updatedAt: string
}

export interface ConnectionsFile {
  version: 1
  connections: ConnectionConfig[]
}

/** 列表/返回给渲染进程的脱敏视图：passwordEnc 一律不外传（data-model.md 红线） */
export type ConnectionListItem = Omit<ConnectionConfig, 'passwordEnc' | 'sshTunnel'> & {
  hasPassword: boolean
  sshTunnel: SshTunnelView | null
}

/** SSH 隧道保存入参（明文经 IPC 进 Main，加密后落盘；渲染层不落任何明文） */
export interface SshTunnelInput {
  enabled: boolean
  host: string
  port: number
  username: string
  authType: SshAuthType
  /** 明文；undefined = 保留原值（与 password 同契约） */
  password?: string
  privateKeyPath?: string
  /** 明文；undefined = 保留原值 */
  passphrase?: string
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
  /** SSH 隧道；undefined = 保留原值，null = 清空 */
  sshTunnel?: SshTunnelInput | null
}

/** secure:decrypt 支持的字段（spec-06 扩展 SSH 两个 secret） */
export type SecretField = 'password' | 'sshPassword' | 'sshPassphrase'

export interface PasswordCipher {
  encrypt(plain: string): string
  /** 解密失败（如换机器后钥匙串不匹配）返回 null */
  decrypt(cipherText: string): string | null
}

export function newConnectionsFile(): ConnectionsFile {
  return { version: 1, connections: [] }
}

function toSshTunnelView(s: SshTunnelSecret): SshTunnelView {
  const { sshPasswordEnc, sshPassphraseEnc, ...rest } = s
  return {
    ...rest,
    hasSshPassword: sshPasswordEnc !== '',
    hasSshPassphrase: sshPassphraseEnc !== '',
  }
}

function toListItem(c: ConnectionConfig): ConnectionListItem {
  const { passwordEnc, sshTunnel, ...rest } = c
  return {
    ...rest,
    hasPassword: passwordEnc !== '',
    sshTunnel: sshTunnel === null ? null : toSshTunnelView(sshTunnel),
  }
}

/** 明文 → 存储态；undefined secret 保留原值（编辑留空 = 不修改，spec-06 第 2 节） */
function buildSshTunnelSecret(
  input: SshTunnelInput,
  existing: SshTunnelSecret | null,
  cipher: PasswordCipher,
): SshTunnelSecret {
  return {
    enabled: input.enabled,
    host: input.host,
    port: input.port,
    username: input.username,
    authType: input.authType,
    sshPasswordEnc:
      input.password === undefined ? (existing?.sshPasswordEnc ?? '') : cipher.encrypt(input.password),
    sshPassphraseEnc:
      input.passphrase === undefined ? (existing?.sshPassphraseEnc ?? '') : cipher.encrypt(input.passphrase),
    privateKeyPath: input.privateKeyPath ?? existing?.privateKeyPath ?? '',
  }
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
      if (input.sshTunnel !== undefined) {
        existing.sshTunnel =
          input.sshTunnel === null ? null : buildSshTunnelSecret(input.sshTunnel, existing.sshTunnel, this.cipher)
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
      sshTunnel:
        input.sshTunnel === undefined || input.sshTunnel === null
          ? null
          : buildSshTunnelSecret(input.sshTunnel, null, this.cipher),
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

  /** IPC: secure:decrypt —— 仅用于编辑表单回填 / 打开会话取明文；解密失败返回 null（spec-01 边界情况） */
  decryptSecret(id: string, field: SecretField = 'password'): string | null {
    const conn = this.load().connections.find((c) => c.id === id)
    if (!conn) return null
    if (field === 'password') {
      if (conn.passwordEnc === '') return ''
      return this.cipher.decrypt(conn.passwordEnc)
    }
    const ssh = conn.sshTunnel
    if (!ssh) return ''
    const enc = field === 'sshPassword' ? ssh.sshPasswordEnc : ssh.sshPassphraseEnc
    if (enc === '') return ''
    return this.cipher.decrypt(enc)
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
