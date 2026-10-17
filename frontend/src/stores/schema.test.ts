import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

/** 排空后台预取等挂起的异步任务，避免跨用例竞态 */
const flushAsync = () => new Promise((r) => setTimeout(r, 0))

const apiMock = vi.hoisted(() => ({
  getSchemas: vi.fn(),
  getTables: vi.fn(),
  getTableDetail: vi.fn(),
}))

vi.mock('@/api/metadata', () => apiMock)

import { useSchemaStore } from './schema'

const schemas = [
  { name: 'joyo', isSystem: false },
  { name: 'mysql', isSystem: true },
]

const tables = [
  { name: 'user', type: 'BASE_TABLE', engine: 'InnoDB', comment: '', rowsEstimate: 10 },
  { name: 'v_user', type: 'VIEW', engine: null, comment: '', rowsEstimate: null },
]

const detail = {
  columns: [
    { name: 'id', dataType: 'bigint', length: null, nullable: false, key: 'PRI',
      defaultValue: null, extra: 'auto_increment', comment: '主键', ordinal: 1 },
  ],
  indexes: [{ name: 'PRIMARY', type: 'BTREE', unique: true, columns: ['id'] }],
  ddl: 'CREATE TABLE `user` (...)',
}

describe('schema store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    apiMock.getSchemas.mockResolvedValue({ schemas })
    apiMock.getTables.mockResolvedValue({ tables })
    apiMock.getTableDetail.mockResolvedValue(detail)
  })

  it('activate 后加载库列表', async () => {
    const store = useSchemaStore()
    await store.activate('conn-1')
    expect(apiMock.getSchemas).toHaveBeenCalledWith('conn-1')
    expect(store.trees['conn-1']?.schemas).toHaveLength(2)
    expect(store.activeConnectionId).toBe('conn-1')
  })

  it('展开库懒加载表；再折叠不重新请求', async () => {
    const store = useSchemaStore()
    await store.activate('conn-1')
    await store.toggleSchema('conn-1', 'joyo')
    await flushAsync()
    expect(apiMock.getTables).toHaveBeenCalledTimes(1)
    expect(store.filterTables('joyo', '')).toHaveLength(2)

    await store.toggleSchema('conn-1', 'joyo') // 折叠
    await store.toggleSchema('conn-1', 'joyo') // 再展开
    expect(apiMock.getTables).toHaveBeenCalledTimes(1) // 缓存生效
  })

  it('selectTable 加载详情并选中；右侧面板取到详情', async () => {
    const store = useSchemaStore()
    await store.activate('conn-1')
    await store.selectTable('conn-1', 'joyo', 'user')

    expect(apiMock.getTableDetail).toHaveBeenCalledWith('conn-1', 'joyo', 'user')
    expect(store.selectedTableLocation).toEqual({ schema: 'joyo', table: 'user' })
    expect(store.selectedDetail()?.ddl).toContain('CREATE TABLE')
  })

  it('表数量超渲染上限：filterTables 截断并报告截断数', async () => {
    const many = Array.from({ length: 500 }, (_, i) => ({
      name: `t${i}`, type: 'BASE_TABLE', engine: 'InnoDB', comment: '', rowsEstimate: 0,
    }))
    apiMock.getTables.mockResolvedValue({ tables: many })

    const store = useSchemaStore()
    await store.activate('conn-1')
    await store.toggleSchema('conn-1', 'joyo')
    await flushAsync()

    expect(store.filterTables('joyo', '').length).toBe(200)
    expect(store.truncatedCount('joyo', '')).toBe(300)
    // 过滤生效（注意 't1' 这类短前缀会匹配大量名字，选精确名字断言）
    expect(store.filterTables('joyo', 't123')).toHaveLength(1)
    expect(store.filterTables('joyo', 't499')).toHaveLength(1)
  })

  it('过滤无匹配时 filteredEmpty 为真（区别于空库）', async () => {
    const store = useSchemaStore()
    await store.activate('conn-1')
    await store.toggleSchema('conn-1', 'joyo')

    await flushAsync()
    expect(store.filteredEmpty('joyo', '不存在的前缀xyz')).toBe(true)
    expect(store.filteredEmpty('joyo', 'user')).toBe(false)
  })

  it('deactivate 清空树并切换激活连接；resetSelection 清选中', async () => {
    const store = useSchemaStore()
    await store.activate('conn-1')
    await store.activate('conn-2')
    await store.selectTable('conn-2', 'joyo', 'user')

    store.deactivate('conn-2')

    expect(store.trees['conn-2']).toBeUndefined()
    expect(store.activeConnectionId).toBe('conn-1')
    expect(store.selectedTableLocation).toBeNull()
  })

  it('refreshTables 刷新缓存', async () => {
    const store = useSchemaStore()
    await store.activate('conn-1')
    await store.toggleSchema('conn-1', 'joyo')
    await flushAsync()
    await store.refreshTables('conn-1', 'joyo')
    await flushAsync()
    expect(apiMock.getTables).toHaveBeenCalledTimes(2)
  })
})
