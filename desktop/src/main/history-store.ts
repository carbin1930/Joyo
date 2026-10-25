import fs from 'node:fs'
import path from 'node:path'

/**
 * 查询历史持久化唯一模块（spec-05，rest-api.md 连接管理节 IPC 白名单）：
 * - 读写只发生在 Electron Main 进程；history.json 与 connections.json 同层、同原子写模式
 * - 实现说明：spec-05 原设计 better-sqlite3，因 desktop 保持零 native 依赖
 *   （避免 electron-rebuild 链路）改为 JSON 存储；上限 1000 条、追加型数据，行为完全等价
 * - 去重：sql 逐字节相同 → 原位更新（不新增行）
 * 本模块不依赖 electron，便于纯 node 单元测试。
 */

export interface HistoryEntryInput {
  sql: string
  connectionName: string
  database: string
  /** SELECT/UPDATE/.../MULTI */
  kind: string
  elapsedMs: number
  rowCount: number | null
  success: boolean
  errorMessage: string | null
  /** ISO-8601 UTC */
  executedAt: string
}

export interface HistoryItem extends HistoryEntryInput {
  id: number
}

interface HistoryFile {
  version: 1
  nextId: number
  items: HistoryItem[]
}

/** spec-05 行为 3：历史上限（超出删最旧） */
export const MAX_HISTORY_ITEMS = 1000

/** IPC: history:list 入参 */
export interface HistoryListQuery {
  keyword?: string
  limit?: number
}

function newHistoryFile(): HistoryFile {
  return { version: 1, nextId: 1, items: [] }
}

export class HistoryStore {
  constructor(private readonly filePath: string) {}

  /** IPC: history:append（去重 + 上限裁剪 + 原子写） */
  append(entry: HistoryEntryInput): void {
    if (!entry.sql.trim()) return
    const file = this.load()
    const existing = file.items.find((i) => i.sql === entry.sql)
    if (existing) {
      // 行为 2：相同 SQL 原位更新，不新增行
      Object.assign(existing, entry)
    } else {
      file.items.push({ id: file.nextId, ...entry })
      file.nextId += 1
    }
    if (file.items.length > MAX_HISTORY_ITEMS) {
      file.items = file.items.slice(-MAX_HISTORY_ITEMS)
    }
    this.persist(file)
  }

  /** IPC: history:list（executedAt 倒序；keyword 大小写不敏感子串匹配） */
  list(query: HistoryListQuery = {}): HistoryItem[] {
    const limit = Math.min(Math.max(query.limit ?? 50, 1), 200)
    const keyword = (query.keyword ?? '').toLowerCase()
    return this.load()
      .items.filter((i) => (keyword ? i.sql.toLowerCase().includes(keyword) : true))
      .sort(compareDesc)
      .slice(0, limit)
  }

  /** IPC: history:delete；返回是否删除了条目 */
  remove(id: number): boolean {
    const file = this.load()
    const before = file.items.length
    file.items = file.items.filter((i) => i.id !== id)
    if (file.items.length === before) return false
    this.persist(file)
    return true
  }

  /** IPC: history:clear */
  clear(): void {
    this.persist(newHistoryFile())
  }

  private load(): HistoryFile {
    if (!fs.existsSync(this.filePath)) return newHistoryFile()
    try {
      const raw = JSON.parse(fs.readFileSync(this.filePath, 'utf-8')) as HistoryFile
      if (raw.version !== 1 || !Array.isArray(raw.items)) {
        throw new Error('version 或 items 字段不符')
      }
      return raw
    } catch (e) {
      // 与 connections.json 同规则：损坏文件重命名保留现场，新建空文件
      const brokenPath = `${this.filePath}.broken.${Date.now()}`
      fs.renameSync(this.filePath, brokenPath)
      console.warn(`[history-store] 历史文件损坏，已重命名为 ${brokenPath}: ${String(e)}`)
      return newHistoryFile()
    }
  }

  private persist(file: HistoryFile): void {
    const tmpPath = `${this.filePath}.tmp`
    fs.writeFileSync(tmpPath, JSON.stringify(file), 'utf-8')
    fs.renameSync(tmpPath, this.filePath)
  }
}

/** executedAt 倒序（ISO-8601 字符串可直接比较）；同时间戳按 id 倒序 */
function compareDesc(a: HistoryItem, b: HistoryItem): number {
  if (a.executedAt !== b.executedAt) return a.executedAt < b.executedAt ? 1 : -1
  return b.id - a.id
}

/** 便捷构造：userData 目录下的标准历史文件路径 */
export function historyFilePath(userDataDir: string): string {
  return path.join(userDataDir, 'history.json')
}
