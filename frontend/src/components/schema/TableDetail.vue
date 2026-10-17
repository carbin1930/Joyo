<script setup lang="ts">
import { computed, ref } from 'vue'
import { useSchemaStore } from '@/stores/schema'
import { toast } from '@/composables/use-toast'
import { zhCN } from '@/locales/zh-CN'
import type { TableColumn } from '@/types/api'

/**
 * 表详情面板（spec-02 第 2 节）：三个 Tab——列 / 索引 / DDL，DDL 可复制。
 */

const schemaStore = useSchemaStore()
const activeTab = ref<'columns' | 'indexes' | 'ddl'>('columns')

const location = computed(() => schemaStore.selectedTableLocation)
const detail = computed(() => schemaStore.selectedDetail())

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
    </div>

    <div v-if="!location" class="detail-empty">
      {{ zhCN.schema.detail.noSelection }}
    </div>
    <div v-else-if="!detail && schemaStore.detailLoading" class="detail-empty">
      {{ zhCN.schema.loading }}
    </div>
    <div v-else-if="!detail" class="detail-empty">{{ zhCN.schema.detail.loadFailed }}</div>

    <template v-else>
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
  gap: 12px;
}

.detail-header {
  display: flex;
  align-items: center;
}

.detail-title {
  font-size: 18px;
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
  gap: 4px;
  border-bottom: 1px solid var(--joyo-border);
}

.tab {
  border: none;
  background: none;
  padding: 8px 14px;
  font-size: 13px;
  cursor: pointer;
  color: var(--joyo-text-muted);
  border-bottom: 2px solid transparent;
}

.tab.active {
  color: var(--joyo-primary);
  border-bottom-color: var(--joyo-primary);
}

.tab-body {
  flex: 1;
  overflow: auto;
}

.grid {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.grid th {
  text-align: left;
  padding: 8px 10px;
  background: var(--joyo-bg-sidebar);
  border-bottom: 1px solid var(--joyo-border);
  position: sticky;
  top: 0;
}

.grid td {
  padding: 7px 10px;
  border-bottom: 1px solid var(--joyo-border);
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
  padding: 6px 14px;
  border: 1px solid var(--joyo-border);
  border-radius: 6px;
  background: var(--joyo-bg-card);
  cursor: pointer;
  font-size: 13px;
}

.btn:hover {
  border-color: var(--joyo-primary);
}

.ddl-pre {
  margin: 0;
  padding: 14px;
  background: var(--joyo-bg-card);
  border: 1px solid var(--joyo-border);
  border-radius: 8px;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
