<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useConnectionStore } from '@/stores/connection'
import { useQueryStore } from '@/stores/query'
import { useSchemaStore, tableKey, TABLE_RENDER_CHUNK } from '@/stores/schema'
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

/** 当前正在使用的库（USE 切换过则用切换值，否则用连接默认库） */
function currentDbName(): string {
  return (
    (activeId.value ? queryStore.currentDb[activeId.value] : '') ??
    connections.items.find((c) => c.id === activeId.value)?.defaultDatabase ??
    ''
  )
}

function isCurrentSchema(name: string): boolean {
  return name === currentDbName()
}

/** 双击库节点 = 切换到该库（SQLyog 习惯） */
function useSchemaNode(name: string): void {
  if (activeId.value) void queryStore.switchDb(activeId.value, name)
}

function refreshSchema(e: MouseEvent, name: string): void {
  e.stopPropagation()
  if (activeId.value) void schemaStore.refreshTables(activeId.value, name)
}

function onTableClick(schema: string, table: string): void {
  if (!activeId.value) return
  void schemaStore.selectTable(activeId.value, schema, table)
  // 联动：编辑器当前库自动切到该表所在库（已在目标库则跳过）
  const current =
    queryStore.currentDb[activeId.value] ??
    connections.items.find((c) => c.id === activeId.value)?.defaultDatabase ??
    ''
  if (current !== schema) {
    dbBeforeLastSwitch = current
    void queryStore.switchDb(activeId.value, schema)
  }
}

/** 单击上一张表之前所在的库（用于双击插入时判断是否需要库名限定） */
let dbBeforeLastSwitch = ''

/** 双击表：插入表名到光标处——当前库的表只插表名；跨库带库名限定并附同名别名 */
/**
 * 表别名（参考 SQLyog）：按下划线分段取首字母小写。
 * 例：order_item → oi，sys_config → sc，user → user。
 */
function tableAlias(table: string): string {
  const name = table.replace(/`/g, '')
  const parts = name.split('_').filter(Boolean)
  return parts.length > 1 ? parts.map((p) => p[0]).join('').toLowerCase() : name.toLowerCase()
}

/**
 * 双击表：插入表名到光标处（一律带别名）。判断依据是"点击该表之前所在的库"：
 * 同库：`表名` AS 别名；跨库：`库名`.`表名` AS 别名。
 */
function onTableDblClick(schema: string, table: string): void {
  const current =
    queryStore.currentDb[activeId.value ?? ''] ??
    connections.items.find((c) => c.id === activeId.value)?.defaultDatabase ??
    ''
  const base = dbBeforeLastSwitch || current
  const escapedTable = '`' + table.replace(/`/g, '``') + '`'
  const alias = tableAlias(table)
  const text =
    (schema === base
      ? escapedTable
      : '`' + schema.replace(/`/g, '``') + '`.' + escapedTable) + ' AS ' + alias
  queryStore.requestInsert(text)
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
    />

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
          <div
            v-for="t in schemaStore.filterTables(s.name, filter)"
            :key="t.name"
            class="node-row table-row"
            :class="{ selected: isSelected(s.name, t.name) }"
            @click="onTableClick(s.name, t.name)"
            @dblclick="onTableDblClick(s.name, t.name)"
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
            v-if="schemaStore.truncatedCount(s.name, filter) > 0"
            class="tree-empty small"
          >
            {{ zhCN.schema.truncated(TABLE_RENDER_CHUNK, schemaStore.truncatedCount(s.name, filter)) }}
          </div>

          <!-- 展开的表：列清单 -->
          <template
            v-for="t in schemaStore.filterTables(s.name, filter)"
            :key="`cols-${t.name}`"
          >
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
              <div v-if="schemaStore.detailLoading" class="tree-empty small">
                {{ zhCN.schema.loading }}
              </div>
            </div>
          </template>
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
  font-size: 12px;
  color: var(--joyo-text-muted);
}

.refresh-btn {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--joyo-text-muted);
  font-size: 13px;
  padding: 2px 6px;
  border-radius: 4px;
}

.refresh-btn:hover {
  background: var(--joyo-bg-card);
  color: var(--joyo-primary);
}

.filter-input {
  padding: 6px 10px;
  border: 1px solid var(--joyo-border);
  border-radius: 6px;
  font-size: 12px;
  outline: none;
}

.filter-input:focus {
  border-color: var(--joyo-primary);
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
  gap: 4px;
  padding: 4px 6px;
  border-radius: 5px;
  cursor: pointer;
  user-select: none;
}

.node-row:hover {
  background: var(--joyo-bg-card);
}

.node-row.current .node-name {
  color: var(--joyo-primary);
  font-weight: 600;
}

.node-row.selected {
  background: var(--joyo-bg-card);
  outline: 1px solid var(--joyo-primary);
}

.arrow {
  width: 14px;
  flex-shrink: 0;
  color: var(--joyo-text-muted);
  font-size: 10px;
}

.node-name {
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rows {
  margin-left: auto;
  font-size: 11px;
  color: var(--joyo-text-muted);
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
  gap: 8px;
  padding: 2px 6px;
  font-size: 12px;
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
