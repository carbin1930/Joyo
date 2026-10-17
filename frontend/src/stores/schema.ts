import { defineStore } from 'pinia'
import { getTableDetail, getSchemas, getTables } from '@/api/metadata'
import { toast } from '@/composables/use-toast'
import type { SchemaItem, TableDetailResponse, TableItem } from '@/types/api'

/** 树节点缓存的 key */
export function tableKey(schema: string, table: string): string {
  return `${schema}.${table}`
}

/** 一个已连接连接的对象树状态（spec-02） */
interface TreeState {
  schemas: SchemaItem[]
  schemasLoading: boolean
  tablesBySchema: Record<string, TableItem[]>
  detailByKey: Record<string, TableDetailResponse>
  expandedSchemas: Record<string, boolean>
  expandedTables: Record<string, boolean>
}

/** 单表展开渲染上限（spec-02 边界情况：>1000 表不一次性渲染全部 DOM） */
export const TABLE_RENDER_CHUNK = 200

/**
 * schema 树状态：
 * - activeConnectionId：当前展示树结构的连接（选中且已连接，否则最近连接的）
 * - 懒加载：库 → 表 → 列（列来自表详情缓存）
 * - 断开连接 → 对应树清空（spec-02 行为细节 6）
 */
export const useSchemaStore = defineStore('schema', {
  state: () => ({
    trees: {} as Record<string, TreeState>,
    activeConnectionId: null as string | null,
    selectedTableKey: null as string | null,
    selectedTableLocation: null as { schema: string; table: string } | null,
    detailLoading: false,
  }),

  getters: {
    activeTree(state): TreeState | null {
      return state.activeConnectionId ? (state.trees[state.activeConnectionId] ?? null) : null
    },
  },

  actions: {
    /** 连接成功后调用：切换/初始化该连接的树并加载库列表 */
    async activate(connectionId: string): Promise<void> {
      this.activeConnectionId = connectionId
      if (!this.trees[connectionId]) {
        this.trees[connectionId] = {
          schemas: [],
          schemasLoading: false,
          tablesBySchema: {},
          detailByKey: {},
          expandedSchemas: {},
          expandedTables: {},
        }
      }
      await this.refreshSchemas(connectionId)
    },

    /** 连接断开：清空对应树与选中状态；若激活的树被清空则切换到其他已连接连接 */
    deactivate(connectionId: string): void {
      delete this.trees[connectionId]
      this.resetSelection()
      if (this.activeConnectionId === connectionId) {
        this.activeConnectionId = Object.keys(this.trees)[0] ?? null
      }
    },

    async refreshSchemas(connectionId: string): Promise<void> {
      const tree = this.trees[connectionId]
      if (!tree) return
      tree.schemasLoading = true
      try {
        tree.schemas = (await getSchemas(connectionId)).schemas
      } catch (e) {
        toast('error', e instanceof Error ? e.message : String(e))
      } finally {
        tree.schemasLoading = false
      }
    },

    /** 展开库：懒加载表列表；超过渲染上限时仅渲染前 N 个（配合过滤使用） */
    async toggleSchema(connectionId: string, schema: string): Promise<void> {
      const tree = this.trees[connectionId]
      if (!tree) return
      if (tree.expandedSchemas[schema]) {
        tree.expandedSchemas[schema] = false
        return
      }
      tree.expandedSchemas[schema] = true
      if (!tree.tablesBySchema[schema]) {
        try {
          tree.tablesBySchema[schema] = (await getTables(connectionId, schema)).tables
        } catch (e) {
          tree.expandedSchemas[schema] = false
          delete tree.tablesBySchema[schema]
          toast('error', e instanceof Error ? e.message : String(e))
        }
      }
    },

    /** 展开表：加载表详情（树列清单与右侧详情共用缓存） */
    async toggleTable(connectionId: string, schema: string, table: string): Promise<void> {
      const tree = this.trees[connectionId]
      if (!tree) return
      const key = tableKey(schema, table)
      tree.expandedTables[key] = !tree.expandedTables[key]
      if (tree.expandedTables[key]) {
        await this.ensureDetail(connectionId, schema, table)
      }
    },

    /** 单击表节点：选中并展开加载（右侧详情联动） */
    async selectTable(connectionId: string, schema: string, table: string): Promise<void> {
      const tree = this.trees[connectionId]
      if (!tree) return
      this.selectedTableKey = tableKey(schema, table)
      this.selectedTableLocation = { schema, table }
      if (!tree.detailByKey[tableKey(schema, table)]) {
        this.detailLoading = true
        try {
          tree.detailByKey[tableKey(schema, table)] = await getTableDetail(
            connectionId,
            schema,
            table,
          )
        } catch (e) {
          toast('error', e instanceof Error ? e.message : String(e))
        } finally {
          this.detailLoading = false
        }
      }
      tree.expandedTables[tableKey(schema, table)] = true
    },

    async ensureDetail(connectionId: string, schema: string, table: string): Promise<void> {
      const tree = this.trees[connectionId]
      if (!tree || tree.detailByKey[tableKey(schema, table)]) return
      this.detailLoading = true
      try {
        tree.detailByKey[tableKey(schema, table)] = await getTableDetail(connectionId, schema, table)
      } catch (e) {
        toast('error', e instanceof Error ? e.message : String(e))
      } finally {
        this.detailLoading = false
      }
    },

    /** 刷新某库的表列表（spec-02：每级可刷新；保持展开状态直接重拉） */
    async refreshTables(connectionId: string, schema: string): Promise<void> {
      const tree = this.trees[connectionId]
      if (!tree) return
      delete tree.tablesBySchema[schema]
      if (tree.expandedSchemas[schema]) {
        try {
          tree.tablesBySchema[schema] = (await getTables(connectionId, schema)).tables
        } catch (e) {
          toast('error', e instanceof Error ? e.message : String(e))
        }
      }
    },

    /** 选中表对应的详情（右侧面板） */
    selectedDetail(): TableDetailResponse | null {
      if (!this.selectedTableKey || !this.activeConnectionId) return null
      return this.trees[this.activeConnectionId]?.detailByKey[this.selectedTableKey] ?? null
    },

    /** 名称过滤（spec-02：过滤当前已加载节点，纯前端） */
    filterTables(schema: string, keyword: string): TableItem[] {
      const tree = this.activeTree
      if (!tree) return []
      const all = tree.tablesBySchema[schema] ?? []
      const kw = keyword.trim().toLowerCase()
      const matched = kw ? all.filter((t) => t.name.toLowerCase().includes(kw)) : all
      return matched.slice(0, TABLE_RENDER_CHUNK)
    },

    /** 过滤命中数超出渲染上限时给出提示 */
    truncatedCount(schema: string, keyword: string): number {
      const tree = this.activeTree
      if (!tree) return 0
      const kw = keyword.trim().toLowerCase()
      const matched = kw
        ? (tree.tablesBySchema[schema] ?? []).filter((t) => t.name.toLowerCase().includes(kw))
        : (tree.tablesBySchema[schema] ?? [])
      return Math.max(0, matched.length - TABLE_RENDER_CHUNK)
    },

    /** 无匹配时提示（区别于空库） */
    hasTables(schema: string): boolean {
      return (this.activeTree?.tablesBySchema[schema]?.length ?? 0) > 0
    },

    /** 过滤后为空但库本身有表 */
    filteredEmpty(schema: string, keyword: string): boolean {
      return this.hasTables(schema) && this.filterTables(schema, keyword).length === 0
    },

    /** 选中状态重置（连接断开时由组件层调用；树缓存随 trees 删除） */
    resetSelection(): void {
      this.selectedTableKey = null
      this.selectedTableLocation = null
    },
  },
})
