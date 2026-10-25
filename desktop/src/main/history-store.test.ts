import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { HistoryStore, MAX_HISTORY_ITEMS, type HistoryEntryInput } from './history-store'

/** spec-05 行为细节与边界情况测试 */
describe('HistoryStore', () => {
  let dir: string
  let store: HistoryStore

  function entry(overrides: Partial<HistoryEntryInput> = {}): HistoryEntryInput {
    return {
      sql: 'SELECT 1',
      connectionName: 'local',
      database: 'joyo',
      kind: 'SELECT',
      elapsedMs: 12,
      rowCount: 1,
      success: true,
      errorMessage: null,
      executedAt: '2026-10-25T10:00:00Z',
      ...overrides,
    }
  }

  beforeEach(() => {
    dir = fs.mkdtempSync(path.join(os.tmpdir(), 'joyo-history-'))
    store = new HistoryStore(path.join(dir, 'history.json'))
  })

  afterEach(() => {
    fs.rmSync(dir, { recursive: true, force: true })
  })

  it('append 新增后 list 按 executedAt 倒序返回', () => {
    store.append(entry({ sql: 'SELECT 1', executedAt: '2026-10-25T10:00:00Z' }))
    store.append(entry({ sql: 'SELECT 2', executedAt: '2026-10-25T10:01:00Z' }))

    const items = store.list()
    expect(items.map((i) => i.sql)).toEqual(['SELECT 2', 'SELECT 1'])
    expect(items[0].id).toBe(2)
  })

  it('append 相同 SQL 原位更新，不新增行', () => {
    store.append(entry({ rowCount: 1, executedAt: '2026-10-25T10:00:00Z' }))
    store.append(entry({ rowCount: 5, executedAt: '2026-10-25T10:05:00Z' }))

    const items = store.list()
    expect(items).toHaveLength(1)
    expect(items[0].rowCount).toBe(5)
    expect(items[0].executedAt).toBe('2026-10-25T10:05:00Z')
  })

  it('append 空白 SQL 被忽略', () => {
    store.append(entry({ sql: '   ' }))
    expect(store.list()).toHaveLength(0)
  })

  it('超过上限删除最旧条目', () => {
    for (let i = 0; i < MAX_HISTORY_ITEMS + 5; i++) {
      store.append(entry({ sql: `SELECT ${i}`, executedAt: new Date(Date.parse('2026-10-25T00:00:00Z') + i * 1000).toISOString() }))
    }
    expect(store.list({ limit: 200 })).toHaveLength(200)
    const all = store.list({ limit: 200, keyword: 'select' })
    expect(all[0].sql).toBe(`SELECT ${MAX_HISTORY_ITEMS + 4}`)
  })

  it('list 关键词搜索大小写不敏感', () => {
    store.append(entry({ sql: 'SELECT * FROM User' }))
    store.append(entry({ sql: 'UPDATE t SET a = 1' }))

    expect(store.list({ keyword: 'from user' })).toHaveLength(1)
    expect(store.list({ keyword: 'USER' })).toHaveLength(1)
    expect(store.list({ keyword: 'update' })).toHaveLength(1)
  })

  it('list limit 默认 50、最大 200', () => {
    for (let i = 0; i < 60; i++) {
      store.append(entry({ sql: `SELECT ${i}` }))
    }
    expect(store.list()).toHaveLength(50)
    expect(store.list({ limit: 999 })).toHaveLength(60)
  })

  it('remove 删除指定条目并持久化', () => {
    store.append(entry())
    const item = store.list()[0]
    expect(store.remove(item.id)).toBe(true)
    expect(store.remove(item.id)).toBe(false)
    expect(store.list()).toHaveLength(0)
  })

  it('clear 清空全部并持久化', () => {
    store.append(entry())
    store.clear()
    expect(store.list()).toHaveLength(0)
    // 重新实例化（模拟重启）后仍为空
    expect(new HistoryStore(path.join(dir, 'history.json')).list()).toHaveLength(0)
  })

  it('重启后数据仍在（持久化）', () => {
    store.append(entry({ sql: 'SELECT 1' }))
    const reloaded = new HistoryStore(path.join(dir, 'history.json'))
    expect(reloaded.list()).toHaveLength(1)
  })

  it('损坏文件重命名兜底并返回空', () => {
    const file = path.join(dir, 'history.json')
    fs.writeFileSync(file, '{ broken', 'utf-8')
    expect(store.list()).toHaveLength(0)
    const broken = fs.readdirSync(dir).find((f) => f.includes('.broken.'))
    expect(broken).toBeDefined()
  })
})
