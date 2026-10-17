import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const apiMock = vi.hoisted(() => ({
  executeQuery: vi.fn(),
  getQueryRows: vi.fn(),
}))

vi.mock('@/api/query', () => apiMock)

import { useQueryStore } from './query'

function selectResult(ordinal: number, total: number) {
  return {
    ordinal,
    kind: 'SELECT' as const,
    queryId: `q-${ordinal}`,
    columns: [{ name: 'id', type: 'INT', nullable: true }],
    totalKnown: true,
    totalRows: total,
    truncated: false,
    rows: [[String(ordinal)]],
    page: 1,
    pageSize: 100,
    elapsedMs: 5,
  }
}

describe('query store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('草稿按连接隔离', () => {
    const store = useQueryStore()
    store.setDraft('conn-1', 'SELECT 1')
    store.setDraft('conn-2', 'SELECT 2')
    expect(store.draft('conn-1')).toBe('SELECT 1')
    expect(store.draft('conn-2')).toBe('SELECT 2')
  })

  it('execute：多语句生成多个结果 Tab，激活第一个', async () => {
    apiMock.executeQuery.mockResolvedValue({
      statements: [selectResult(1, 3), selectResult(2, 3), selectResult(3, 3)],
      elapsedMs: 10,
    })
    const store = useQueryStore()
    await store.execute('conn-1', 'SELECT 1; SELECT 2; SELECT 3')

    expect(store.tabs).toHaveLength(3)
    expect(store.tabs[0].kind).toBe('SELECT')
    expect(store.tabs[0].title).toContain('1/3')
    expect(store.activeTabId).toBe(store.tabs[0].id)
    expect(store.executing).toBe(false)
  })

  it('execute：空白 SQL 不调用接口', async () => {
    const store = useQueryStore()
    await store.execute('conn-1', '   ')
    expect(apiMock.executeQuery).not.toHaveBeenCalled()
    expect(store.tabs).toHaveLength(0)
  })

  it('execute：失败生成错误 Tab', async () => {
    apiMock.executeQuery.mockRejectedValue(new Error("Unknown column 'nmae'"))
    const store = useQueryStore()
    await store.execute('conn-1', 'SELECT nmae')

    expect(store.tabs).toHaveLength(1)
    expect(store.tabs[0].kind).toBe('ERROR')
    expect(store.tabs[0].errorMessage).toContain('nmae')
    expect(store.activeTabId).toBe(store.tabs[0].id)
  })

  it('changePage：服务端分页更新 Tab 行数据', async () => {
    apiMock.executeQuery.mockResolvedValue({
      statements: [selectResult(1, 250)],
      elapsedMs: 5,
    })
    apiMock.getQueryRows.mockResolvedValue({
      queryId: 'q-1',
      rows: [['201']],
      page: 3,
      pageSize: 100,
      totalKnown: true,
      totalRows: 250,
    })

    const store = useQueryStore()
    await store.execute('conn-1', 'SELECT 1')
    const tab = store.tabs[0]
    await store.changePage(tab, 3)

    expect(apiMock.getQueryRows).toHaveBeenCalledWith('q-1', 3, 100)
    expect(tab.statement?.rows).toEqual([['201']])
    expect(tab.statement?.page).toBe(3)
  })
})
