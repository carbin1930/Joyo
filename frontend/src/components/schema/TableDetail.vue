<script setup lang="ts">
import { computed, ref } from 'vue'
import { useSchemaStore } from '@/stores/schema'
import { toast } from '@/composables/use-toast'
import ExportDialog from '@/components/export/ExportDialog.vue'
import SyncDialog from '@/components/sync/SyncDialog.vue'
import { zhCN } from '@/locales/zh-CN'
import type { TableColumn } from '@/types/api'

/**
 * 表详情面板（spec-02 第 2 节）：三个 Tab——列 / 索引 / DDL，DDL 可复制。
 * spec-15：顶部"导出数据"入口（SELECT * 整表导出）。
 */

const schemaStore = useSchemaStore()
const activeTab = ref<'columns' | 'indexes' | 'ddl'>('columns')

const location = computed(() => schemaStore.selectedTableLocation)
const detail = computed(() => schemaStore.selectedDetail())

/** 整表导出（spec-15 第 1 节入口 2） */
const exportOpen = ref(false)
const exportSource = computed(() => {
  const loc = location.value
  const connectionId = schemaStore.activeConnectionId
  if (!loc || !connectionId) return null
  return {
    connectionId,
    database: loc.schema,
    sql: `SELECT * FROM \`${loc.schema}\`.\`${loc.table}\``,
    defaultTable: loc.table,
  }
})

/** spec-17：同步入口——源为当前表，目标在面板里选 */
const syncOpen = ref(false)
const syncSource = computed(() => {
  const loc = location.value
  const connectionId = schemaStore.activeConnectionId
  if (!loc || !connectionId) return null
  return { connectionId, database: loc.schema, table: loc.table }
})

function keyLabel(key: string | null): string {
  switch (key) {
    case 'PRI':
      return zhCN.schema.detail.key.primary
    case 'UNI':
      return zhCN.schema.detail.key.unique
    case 'MUL':
      return zhCN.schema.detail.key.index
    default:
      return ''
  }
}

async function copyDdl(): Promise<void> {
  if (!detail.value) return
  try {
    await navigator.clipboard.writeText(detail.value.ddl)
    toast('success', zhCN.schema.detail.copied)
  } catch {
    toast('error', zhCN.schema.detail.copyFailed)
  }
}

function closeDetail(): void {
  schemaStore.resetSelection()
}

function columnTypeText(col: TableColumn): string {
  return col.length != null ? `${col.dataType}(${col.length})` : col.dataType
}
</script>

<template>
  <section class="table-detail" data-testid="table-detail">
    <div class="detail-header">
      <h2 class="detail-title">
        <template v-if="location">{{ location.schema }}.{{ location.table }}</template>
      </h2>
      <span class="header-spacer" />
      <!-- spec-15：整表导出入口 -->
      <button
        v-if="location && exportSource"
        class="close-btn"
        data-testid="export-data-btn"
        @click="exportOpen = true"
      >
        {{ zhCN.export.openData }}
      </button>
      <!-- spec-17：以当前表为源发起同步 -->
      <button
        v-if="syncSource"
        class="close-btn"
        data-testid="sync-data-btn"
        @click="syncOpen = true"
      >
        {{ zhCN.sync.open }}
      </button>
      <button class="close-btn" data-testid="back-to-editor" @click="closeDetail">
        {{ zhCN.schema.detail.backToEditor }}
      </button>
    </div>

    <div v-if="!location" class="detail-empty">
      {{ zhCN.schema.detail.noSelection }}
    </div>
    <div v-else-if="!detail && schemaStore.detailLoading" class="detail-empty">
      {{ zhCN.schema.loading }}
    </div>
    <div v-else-if="!detail" class="detail-empty">{{ zhCN.schema.detail.loadFailed }}</div>

    <template v-else>
      <ExportDialog :open="exportOpen" :source="exportSource" @close="exportOpen = false" />
      <SyncDialog :open="syncOpen" :source="syncSource" @close="syncOpen = false" />
      <div class="tabs">
        <button
          class="tab"
          :class="{ active: activeTab === 'columns' }"
          @click="activeTab = 'columns'"
        >
          {{ zhCN.schema.detail.tabs.columns }} ({{ detail.columns.length }})
        </button>
        <button
          class="tab"
          :class="{ active: activeTab === 'indexes' }"
          @click="activeTab = 'indexes'"
        >
          {{ zhCN.schema.detail.tabs.indexes }} ({{ detail.indexes.length }})
        </button>
        <button class="tab" :class="{ active: activeTab === 'ddl' }" @click="activeTab = 'ddl'">
          {{ zhCN.schema.detail.tabs.ddl }}
        </button>
      </div>

      <div class="tab-body">
        <table v-if="activeTab === 'columns'" class="grid">
          <thead>
            <tr>
              <th>#</th>
              <th>{{ zhCN.schema.detail.col.name }}</th>
              <th>{{ zhCN.schema.detail.col.type }}</th>
              <th>{{ zhCN.schema.detail.col.nullable }}</th>
              <th>{{ zhCN.schema.detail.col.key }}</th>
              <th>{{ zhCN.schema.detail.col.default }}</th>
              <th>{{ zhCN.schema.detail.col.comment }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="col in detail.columns" :key="col.name">
              <td class="muted">{{ col.ordinal }}</td>
              <td class="strong">{{ col.name }}</td>
              <td>{{ columnTypeText(col) }}</td>
              <td>{{ col.nullable ? 'YES' : 'NO' }}</td>
              <td>{{ keyLabel(col.key) }}</td>
              <td class="muted">{{ col.defaultValue ?? 'NULL' }}</td>
              <td class="muted">{{ col.comment }}</td>
            </tr>
          </tbody>
        </table>

        <table v-else-if="activeTab === 'indexes'" class="grid">
          <thead>
            <tr>
              <th>{{ zhCN.schema.detail.idx.name }}</th>
              <th>{{ zhCN.schema.detail.idx.type }}</th>
              <th>{{ zhCN.schema.detail.idx.unique }}</th>
              <th>{{ zhCN.schema.detail.idx.columns }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="idx in detail.indexes" :key="idx.name">
              <td class="strong">{{ idx.name }}</td>
              <td>{{ idx.type }}</td>
              <td>{{ idx.unique ? 'YES' : 'NO' }}</td>
              <td>{{ idx.columns.join(', ') }}</td>
            </tr>
          </tbody>
        </table>

        <div v-else class="ddl-block">
          <div class="ddl-actions">
            <button class="btn" @click="copyDdl">{{ zhCN.schema.detail.copyDdl }}</button>
          </div>
          <pre class="ddl-pre">{{ detail.ddl }}</pre>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.table-detail {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: var(--joyo-space-3);
  padding: var(--joyo-space-3);
}

.detail-header {
  display: flex;
  align-items: center;
  gap: var(--joyo-space-2);
}

.close-btn {
  height: var(--joyo-h-control-sm);
  padding: 0 10px;
  border: 1px solid var(--joyo-border);
  border-radius: var(--joyo-radius);
  background: var(--joyo-bg-card);
  cursor: pointer;
  font-size: var(--joyo-fs-sm);
  color: var(--joyo-text-muted);
  transition:
    border-color var(--joyo-transition),
    color var(--joyo-transition),
    background-color var(--joyo-transition);
}

.close-btn:hover {
  border-color: var(--joyo-primary);
  color: var(--joyo-primary);
  background: var(--joyo-primary-weak);
}

.header-spacer {
  flex: 1;
}

.detail-title {
  font-size: var(--joyo-fs-lg);
  font-weight: 600;
  margin: 0;
  font-family: Consolas, monospace;
}

.detail-empty {
  color: var(--joyo-text-muted);
  padding: 32px 0;
  text-align: center;
}

.tabs {
  display: flex;
  gap: 2px;
  height: var(--joyo-h-tab);
  padding: 0 var(--joyo-space-2);
  border-bottom: 1px solid var(--joyo-border);
  background: var(--joyo-bg-card);
  border-radius: var(--joyo-radius) var(--joyo-radius) 0 0;
}

.tab {
  border: none;
  background: none;
  height: 100%;
  padding: 0 10px;
  font-size: var(--joyo-fs-sm);
  cursor: pointer;
  color: var(--joyo-text-muted);
  border-bottom: 2px solid transparent;
  transition:
    background-color var(--joyo-transition),
    color var(--joyo-transition);
}

.tab:hover {
  background: var(--joyo-bg-hover);
  color: var(--joyo-text);
}

.tab.active {
  color: var(--joyo-primary);
  border-bottom-color: var(--joyo-primary);
  font-weight: 600;
}

.tab-body {
  flex: 1;
  overflow: auto;
}

.grid {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--joyo-fs-sm);
  background: var(--joyo-bg-card);
}

.grid th {
  text-align: left;
  padding: 6px 10px;
  font-weight: 600;
  color: var(--joyo-text-muted);
  background: var(--joyo-bg-sidebar);
  border-bottom: 1px solid var(--joyo-border);
  position: sticky;
  top: 0;
  z-index: 1;
}

.grid td {
  padding: 5px 10px;
  border-bottom: 1px solid var(--joyo-border);
  color: var(--joyo-text);
}

.grid tbody tr:hover td {
  background: var(--joyo-bg-hover);
}

.strong {
  font-weight: 600;
}

.muted {
  color: var(--joyo-text-muted);
}

.ddl-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ddl-actions {
  display: flex;
  justify-content: flex-end;
}

.btn {
  height: var(--joyo-h-control);
  padding: 0 12px;
  border: 1px solid var(--joyo-border);
  border-radius: var(--joyo-radius);
  background: var(--joyo-bg-card);
  color: var(--joyo-text);
  cursor: pointer;
  font-size: var(--joyo-fs-md);
  transition:
    border-color var(--joyo-transition),
    color var(--joyo-transition),
    background-color var(--joyo-transition);
}

.btn:hover {
  border-color: var(--joyo-primary);
  color: var(--joyo-primary);
  background: var(--joyo-primary-weak);
}

.ddl-pre {
  margin: 0;
  padding: var(--joyo-space-3);
  background: var(--joyo-bg-sidebar);
  border: 1px solid var(--joyo-border);
  border-radius: var(--joyo-radius);
  font-family: Consolas, 'Courier New', monospace;
  font-size: var(--joyo-fs-sm);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
