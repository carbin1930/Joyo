<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useConnectionStore } from '@/stores/connection'
import { useQueryStore } from '@/stores/query'
import { useSchemaStore, tableKey, TABLE_RENDER_CHUNK } from '@/stores/schema'
import { toast } from '@/composables/use-toast'
import { zhCN } from '@/locales/zh-CN'

/**
 * 对象树（spec-02 第 2 节）：连接 → 库 → 表 → 列；懒加载 + 过滤 + 层级刷新。
 * 仅展示已连接的连接（取选中连接或最近连接的）。
 */

const connections = useConnectionStore()
const schemaStore = useSchemaStore()
const queryStore = useQueryStore()

const filter = ref('')

const activeId = computed(() => connections.connectedIds[0] ?? null)
const tree = computed(() => (activeId.value ? schemaStore.trees[activeId.value] : null))

// 激活连接变化时重置选中表
watch(activeId, () => {
  schemaStore.resetSelection()
  filter.value = ''
})

// 连接成功后自动加载库列表
watch(
  () => connections.connectedIds.slice(),
  async (ids, oldIds = []) => {
    const fresh = ids.filter((id) => !oldIds.includes(id))
    for (const id of fresh) {
      await schemaStore.activate(id)
    }
  },
)

function toggleSchema(name: string): void {
  if (activeId.value) void schemaStore.toggleSchema(activeId.value, name)
}

/** 当前正在使用的库 = 活动 SQL 页签绑定的库（未选库则用连接默认库） */
function currentDbName(): string {
  const tabDb = queryStore.activeSqlTab?.db ?? ''
  if (tabDb) return tabDb
  return connections.items.find((c) => c.id === activeId.value)?.defaultDatabase ?? ''
}

function isCurrentSchema(name: string): boolean {
  return name === currentDbName()
}

/** 双击库节点 = 本页签切换到该库（SQLyog 习惯） */
function useSchemaNode(name: string): void {
  const tabId = queryStore.activeSqlTab?.id
  if (tabId) queryStore.setTabDb(tabId, name)
}

function refreshSchema(e: MouseEvent, name: string): void {
  e.stopPropagation()
  if (activeId.value) void schemaStore.refreshTables(activeId.value, name)
}

function onTableClick(schema: string, table: string): void {
  if (!activeId.value) return
  void schemaStore.selectTable(activeId.value, schema, table)
  // 联动：活动 SQL 页签的库自动切到该表所在库（执行时后端按页签 USE）
  const tabId = queryStore.activeSqlTab?.id
  if (tabId) queryStore.setTabDb(tabId, schema)
}

/**
 * 双击表名的数据预览行数（spec-02 补充，2026-11-06 定为 200）：
 * 200 与 DBeaver/DataGrip 默认抓取量级一致，首屏快（宽表/BLOB 尤其明显），
 * 且远小于「行数限制」默认 1000，不会因截断导致预览结果不可编辑。
 */
const DATA_PREVIEW_LIMIT = 200

function escapeIdent(name: string): string {
  return '`' + name.replace(/`/g, '``') + '`'
}

/**
 * 双击表名：新建 SQL 页签并直接查看该表数据（默认前 1000 条，spec-02 补充）。
 * 用带库名的全限定名，避免页签默认库不同导致查错表。
 */
async function viewTableData(schema: string, table: string): Promise<void> {
  const connectionId = activeId.value
  if (!connectionId) return
  const tab = queryStore.createTab(connectionId, schema)
  const sql =
    'SELECT * FROM ' + escapeIdent(schema) + '.' + escapeIdent(table) + ' LIMIT ' + DATA_PREVIEW_LIMIT
  queryStore.drafts[tab.id] = sql
  await queryStore.execute(tab.id, sql)
}

/**
 * Ctrl/⌘ + 双击表名：把表名插入到活动 SQL 编辑器光标处（原双击行为，保留为快捷键）。
 * 与活动页签同库：`表名`；跨库：`库名`.`表名`（反引号转义，名字含特殊字符也安全）。
 */
function insertTableName(schema: string, table: string): void {
  const tab = queryStore.activeSqlTab
  if (!tab) {
    toast('info', zhCN.schema.dblClickNoTab)
    return
  }
  const escapedTable = escapeIdent(table)
  const text = schema === tab.db ? escapedTable : escapeIdent(schema) + '.' + escapedTable
  queryStore.requestInsert(text)
}

function onTableDblClick(e: MouseEvent, schema: string, table: string): void {
  if (e.ctrlKey || e.metaKey) {
    insertTableName(schema, table)
    return
  }
  void viewTableData(schema, table)
}

function onTableToggle(e: MouseEvent, schema: string, table: string): void {
  e.stopPropagation()
  if (activeId.value) void schemaStore.toggleTable(activeId.value, schema, table)
}

function isSelected(schema: string, table: string): boolean {
  return schemaStore.selectedTableKey === tableKey(schema, table)
}
</script>

<template>
  <div class="schema-tree">
    <div class="tree-header">
      <span class="tree-title">{{ zhCN.schema.title }}</span>
      <button
        v-if="activeId"
        class="refresh-btn"
        :title="zhCN.schema.refresh"
        @click="schemaStore.refreshSchemas(activeId)"
      >
        ⟳
      </button>
    </div>

    <input
      v-model="filter"
      class="filter-input"
      type="text"
      :placeholder="zhCN.schema.filterPlaceholder"
    >

    <div v-if="!activeId" class="tree-empty">{{ zhCN.schema.noConnection }}</div>
    <div v-else-if="tree?.schemasLoading" class="tree-empty">{{ zhCN.schema.loading }}</div>

    <div v-else class="tree-body">
      <div v-for="s in tree?.schemas ?? []" :key="s.name" class="schema-node">
        <div
          class="node-row"
          :class="{ current: isCurrentSchema(s.name) }"
          :title="isCurrentSchema(s.name) ? zhCN.schema.currentDbTitle : zhCN.schema.useDbTitle"
          @click="toggleSchema(s.name)"
          @dblclick="useSchemaNode(s.name)"
        >
          <span class="arrow">{{ tree?.expandedSchemas[s.name] ? '▾' : '▸' }}</span>
          <span class="node-name">{{ s.name }}</span>
          <button
            class="refresh-btn"
            :title="zhCN.schema.refresh"
            @click="refreshSchema($event, s.name)"
          >
            ⟳
          </button>
        </div>

        <div v-if="tree?.expandedSchemas[s.name]" class="child-block">
          <div v-if="schemaStore.filteredEmpty(s.name, filter)" class="tree-empty small">
            {{ zhCN.schema.noMatch }}
          </div>
          <!-- 表行 + 紧随其后的列清单（列必须嵌在该表下方，而不是所有表名之后） -->
          <template v-for="t in schemaStore.filterTables(s.name, filter)" :key="t.name">
            <div
              class="node-row table-row"
              :class="{ selected: isSelected(s.name, t.name) }"
              @click="onTableClick(s.name, t.name)"
              @dblclick="onTableDblClick($event, s.name, t.name)"
            >
              <span class="arrow" @click="onTableToggle($event, s.name, t.name)">
                {{ tree?.expandedTables[tableKey(s.name, t.name)] ? '▾' : '▸' }}
              </span>
              <span class="type-badge" :class="t.type === 'VIEW' ? 'badge-view' : 'badge-table'">
                {{ t.type === 'VIEW' ? 'V' : 'T' }}
              </span>
              <span class="node-name" :title="t.comment || t.name">{{ t.name }}</span>
              <span v-if="t.rowsEstimate != null" class="rows">{{ t.rowsEstimate }}</span>
            </div>

            <div
              v-if="tree?.expandedTables[tableKey(s.name, t.name)]"
              class="columns-block"
            >
              <div
                v-for="col in tree?.detailByKey[tableKey(s.name, t.name)]?.columns ?? []"
                :key="col.name"
                class="column-row"
                :title="col.comment"
              >
                <span class="col-name">{{ col.name }}</span>
                <span class="col-type">
                  {{ col.dataType }}{{ col.key === 'PRI' ? ' · PK' : '' }}
                </span>
              </div>
              <div
                v-if="schemaStore.detailLoading && !tree?.detailByKey[tableKey(s.name, t.name)]"
                class="tree-empty small"
              >
                {{ zhCN.schema.loading }}
              </div>
            </div>
          </template>

          <div
            v-if="schemaStore.truncatedCount(s.name, filter) > 0"
            class="tree-empty small"
          >
            {{ zhCN.schema.truncated(TABLE_RENDER_CHUNK, schemaStore.truncatedCount(s.name, filter)) }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.schema-tree {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
  flex: 1;
}

.tree-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tree-title {
  font-size: var(--joyo-fs-xs);
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--joyo-text-muted);
}

.refresh-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  background: none;
  cursor: pointer;
  color: var(--joyo-text-muted);
  font-size: var(--joyo-fs-md);
  border-radius: var(--joyo-radius-sm);
  transition:
    background-color var(--joyo-transition),
    color var(--joyo-transition);
}

.refresh-btn:hover {
  background: var(--joyo-bg-hover);
  color: var(--joyo-primary);
}

.filter-input {
  height: var(--joyo-h-control);
  padding: 0 10px;
  border: 1px solid var(--joyo-border);
  border-radius: var(--joyo-radius);
  background: var(--joyo-bg-card);
  font-size: var(--joyo-fs-sm);
  color: var(--joyo-text);
  outline: none;
  transition:
    border-color var(--joyo-transition),
    box-shadow var(--joyo-transition);
}

.filter-input::placeholder {
  color: var(--joyo-text-subtle);
}

.filter-input:focus {
  border-color: var(--joyo-primary);
  box-shadow: 0 0 0 2px var(--joyo-primary-weak);
}

.tree-empty {
  color: var(--joyo-text-muted);
  font-size: 12px;
  padding: 8px 4px;
}

.tree-empty.small {
  padding: 4px 8px 4px 28px;
}

.tree-body {
  overflow: auto;
  min-height: 0;
}

.node-row {
  display: flex;
  align-items: center;
  gap: var(--joyo-space-1);
  min-height: 26px;
  padding: 2px 6px;
  border-radius: var(--joyo-radius-sm);
  cursor: pointer;
  user-select: none;
  transition: background-color var(--joyo-transition);
}

.node-row:hover {
  background: var(--joyo-bg-hover);
}

.node-row.current .node-name {
  color: var(--joyo-primary);
  font-weight: 600;
}

/* 选中：主色弱底 + 左侧 2px 主色条（与连接列表一致） */
.node-row.selected {
  background: var(--joyo-primary-weak);
  box-shadow: inset 2px 0 0 var(--joyo-primary);
}

.node-row.selected .node-name {
  color: var(--joyo-primary);
  font-weight: 600;
}

.arrow {
  width: 14px;
  flex-shrink: 0;
  color: var(--joyo-text-muted);
  font-size: 10px;
}

.node-name {
  font-size: var(--joyo-fs-md);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rows {
  margin-left: auto;
  font-size: var(--joyo-fs-xs);
  color: var(--joyo-text-subtle);
  font-variant-numeric: tabular-nums;
}

.type-badge {
  flex-shrink: 0;
  width: 14px;
  height: 14px;
  border-radius: 3px;
  font-size: 10px;
  line-height: 14px;
  text-align: center;
  color: #fff;
}

.badge-table {
  background: var(--joyo-primary);
}

.badge-view {
  background: var(--joyo-warning);
}

.child-block {
  padding-left: 14px;
}

.columns-block {
  padding-left: 26px;
}

.column-row {
  display: flex;
  justify-content: space-between;
  gap: var(--joyo-space-2);
  padding: 1px 6px;
  font-size: var(--joyo-fs-sm);
  color: var(--joyo-text-muted);
}

.col-name {
  color: var(--joyo-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.col-type {
  color: var(--joyo-text-muted);
  flex-shrink: 0;
}
</style>
