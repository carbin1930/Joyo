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

describe('query store（多 SQL 页签）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('ensureTabFor：同连接复用页签，不同连接各自建页签', () => {
    const store = useQueryStore()
    const t1 = store.ensureTabFor('conn-1', 'joyo')
    const again = store.ensureTabFor('conn-1', 'luyou')
    const t2 = store.ensureTabFor('conn-2', '')

    expect(again.id).toBe(t1.id)
    // 已有页签不覆盖其库设置（库由选择器/树联动修改）
    expect(again.db).toBe('joyo')
    expect(t2.id).not.toBe(t1.id)
    expect(store.sqlTabs).toHaveLength(2)
    expect(store.mainTabsOrder).toEqual([t1.id, t2.id])
    expect(store.activeMainTabId).toBe(t2.id)
  })

  it('execute：结果存入所属页签，页签间互不干扰', async () => {
    const store = useQueryStore()
    const tabA = store.ensureTabFor('conn-1', 'dbA')
    const tabB = store.ensureTabFor('conn-2', 'dbB')

    apiMock.executeQuery.mockResolvedValueOnce({
      statements: [selectResult(1, 1)],
      elapsedMs: 5,
    })

    await store.execute(tabA.id, 'SELECT 1')

    expect(apiMock.executeQuery).toHaveBeenCalledWith('conn-1', {
      sql: 'SELECT 1',
      maxRows: 1000,
      database: 'dbA',
    })
    expect(store.resultsByTab[tabA.id]).toHaveLength(1)
    expect(store.resultsByTab[tabB.id]).toBeUndefined()
    expect(store.activeResultByTab[tabA.id]).toBe(store.resultsByTab[tabA.id][0].id)
    expect(store.resultsRev).toBe(1)
  })

  it('moveTab：拖拽换位改变页签顺序', () => {
    const store = useQueryStore()
    const t1 = store.createTab('conn-1', '')
    const t2 = store.createTab('conn-1', 'db2')
    store.setMainTab('log')

    store.moveTab('log', t1.id)

    expect(store.mainTabsOrder).toEqual(['log', t1.id, t2.id])
    expect(store.activeMainTabId).toBe('log')

    store.moveTab(t1.id, 'log')

    expect(store.mainTabsOrder).toEqual([t1.id, 'log', t2.id])
  })

  it('execute：空白 SQL 不调用接口', async () => {
    const store = useQueryStore()
    const tab = store.ensureTabFor('conn-1', '')
    await store.execute(tab.id, '   ')
    expect(apiMock.executeQuery).not.toHaveBeenCalled()
  })

  it('execute：失败生成错误 Tab', async () => {
    const store = useQueryStore()
    const tab = store.ensureTabFor('conn-1', '')
    apiMock.executeQuery.mockRejectedValue(new Error("Unknown column 'nmae'"))

    await store.execute(tab.id, 'SELECT nmae')

    const tabs = store.resultsByTab[tab.id]
    expect(tabs).toHaveLength(1)
    expect(tabs[0].kind).toBe('ERROR')
    expect(tabs[0].errorMessage).toContain('nmae')
  })

  it('closeSqlTab：清理草稿与结果，活动页签切换到相邻页签', () => {
    const store = useQueryStore()
    const t1 = store.ensureTabFor('conn-1', '')
    const t2 = store.createTab('conn-1', 'db2')
    store.setDraft(t1.id, 'SELECT 1')
    store.setActiveMainTab(t1.id)

    store.closeSqlTab(t1.id)

    expect(store.sqlTabs.map((t) => t.id)).toEqual([t2.id])
    expect(store.mainTabsOrder).toEqual([t2.id])
    expect(store.draft(t1.id)).toBe('')
    expect(store.activeMainTabId).toBe(t2.id)
  })

  it('changePage：服务端分页更新对应页签的 Tab 行数据', async () => {
    const store = useQueryStore()
    const tab = store.ensureTabFor('conn-1', '')
    apiMock.executeQuery.mockResolvedValue({
      statements: [selectResult(1, 250)],
      elapsedMs: 5,
    })
    await store.execute(tab.id, 'SELECT 1')
    apiMock.getQueryRows.mockResolvedValue({
      queryId: 'q-1',
      rows: [['201']],
      page: 3,
      pageSize: 100,
      totalKnown: true,
      totalRows: 250,
    })

    const resultTab = store.resultsByTab[tab.id][0]
    await store.changePage(resultTab, 3)

    expect(apiMock.getQueryRows).toHaveBeenCalledWith('q-1', 3, 100)
    expect(resultTab.statement?.rows).toEqual([['201']])
    expect(resultTab.statement?.page).toBe(3)
  })
})
