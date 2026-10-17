<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import MonacoEditor from '@/components/query/MonacoEditor.vue'
import ResultPanel from '@/components/query/ResultPanel.vue'
import TableDetail from '@/components/schema/TableDetail.vue'
import { useConnectionStore } from '@/stores/connection'
import { useQueryStore } from '@/stores/query'
import { useSchemaStore } from '@/stores/schema'
import { zhCN } from '@/locales/zh-CN'

/**
 * SQL 编辑器视图（spec-03 第 2 节）：上半编辑器 + 下半结果面板。
 * 目标连接 = 对象树激活的连接（未连接时组件不渲染）。
 */

const connections = useConnectionStore()
const queryStore = useQueryStore()
const schemaStore = useSchemaStore()

const editorRef = ref<InstanceType<typeof MonacoEditor> | null>(null)

/** 下半区页签：执行结果 / 表结构（单击表时自动切到结构，执行 SQL 自动切回结果） */
const bottomTab = ref<'result' | 'schema'>('result')
const selectedTable = computed(() => schemaStore.selectedTableLocation)

watch(
  () => schemaStore.selectedTableLocation,
  (loc) => {
    if (loc) bottomTab.value = 'schema'
  },
)

watch(
  () => queryStore.activeTabId,
  () => {
    bottomTab.value = 'result'
  },
)

const connectionId = computed(() => connections.connectedIds[0] ?? null)
const activeConn = computed(() =>
  connections.items.find((c) => c.id === connectionId.value) ?? null)

/** 库下拉选项：来自对象树已加载的库列表 */
const schemaNames = computed(() =>
  (connectionId.value ? schemaStore.trees[connectionId.value]?.schemas : null)
    ?.map((s) => s.name) ?? [])

/** 当前库：USE 切换过则用切换值，否则用连接的默认库 */
const currentDb = computed(() => {
  if (!connectionId.value) return ''
  return (
    queryStore.currentDb[connectionId.value] ??
    activeConn.value?.defaultDatabase ??
    ''
  )
})

function onDbChange(e: Event): void {
  const value = (e.target as HTMLSelectElement).value
  if (value && connectionId.value) void queryStore.switchDb(connectionId.value, value)
}

const draft = computed({
  get: () => (connectionId.value ? queryStore.draft(connectionId.value) : ''),
  set: (value: string) => {
    if (connectionId.value) queryStore.setDraft(connectionId.value, value)
  },
})

function onRun(sql: string): void {
  if (!connectionId.value) return
  void queryStore.execute(connectionId.value, sql)
}

function onToolbarRun(): void {
  editorRef.value?.requestRun()
}
</script>

<template>
  <section class="editor-view">
    <div class="toolbar">
      <span class="conn-name">{{ activeConn?.name }}</span>
      <select
        v-if="schemaNames.length > 0"
        class="db-select"
        data-testid="db-select"
        :value="currentDb"
        title="当前数据库"
        @change="onDbChange"
      >
        <option v-if="!currentDb" value="">{{ zhCN.query.noDb }}</option>
        <option v-for="s in schemaNames" :key="s" :value="s">{{ s }}</option>
      </select>
      <span class="toolbar-sep" />
      <button
        class="run-btn"
        data-testid="run-btn"
        :disabled="queryStore.executing || !connectionId"
        @click="onToolbarRun"
      >
        {{ queryStore.executing ? zhCN.query.running : zhCN.query.run }}
      </button>
      <span class="toolbar-hint">Ctrl+Enter</span>
      <span class="spacer" />
      <label class="max-rows">
        {{ zhCN.query.maxRowsLabel }}
        <select v-model.number="queryStore.maxRows">
          <option :value="100">100</option>
          <option :value="1000">1000</option>
          <option :value="10000">10000</option>
        </select>
      </label>
    </div>

    <div class="editor-area">
      <MonacoEditor
        ref="editorRef"
        v-model="draft"
        :insert-request="queryStore.insertRequest"
        @run="onRun"
      />
    </div>

    <div class="status-bar">
      <span class="shortcut-hint">{{ zhCN.query.shortcutHint }}</span>
      <span class="spacer" />
      <span class="db-hint">{{ currentDb ? zhCN.query.defaultDb(currentDb) : zhCN.query.noDb }}</span>
    </div>

    <div class="result-area">
      <div class="bottom-tabs">
        <button
          class="bottom-tab"
          :class="{ active: bottomTab === 'result' }"
          data-testid="bottom-tab-result"
          @click="bottomTab = 'result'"
        >
          {{ zhCN.query.bottomResult }}
        </button>
        <button
          v-if="selectedTable"
          class="bottom-tab"
          :class="{ active: bottomTab === 'schema' }"
          data-testid="bottom-tab-schema"
          @click="bottomTab = 'schema'"
        >
          {{ zhCN.query.bottomSchema(selectedTable.schema, selectedTable.table) }}
        </button>
      </div>
      <div class="bottom-content">
        <TableDetail v-if="bottomTab === 'schema' && selectedTable" />
        <ResultPanel v-else />
      </div>
    </div>
  </section>
</template>

<style scoped>
.editor-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  gap: 8px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.run-btn {
  padding: 6px 18px;
  border: none;
  border-radius: 6px;
  background: var(--joyo-primary);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.run-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.toolbar-hint {
  font-size: 11px;
  color: var(--joyo-text-muted);
}

.spacer {
  flex: 1;
}

.max-rows {
  font-size: 12px;
  color: var(--joyo-text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
}

.max-rows select {
  padding: 3px 6px;
  border: 1px solid var(--joyo-border);
  border-radius: 4px;
}

.editor-area {
  flex: 0 0 40%;
  min-height: 180px;
}

.status-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--joyo-text-muted);
  padding: 2px 4px;
}

.conn-name {
  color: var(--joyo-text);
  font-weight: 600;
}

.toolbar-sep {
  width: 1px;
  height: 18px;
  background: var(--joyo-border);
}

.db-hint {
  color: var(--joyo-text-muted);
}

.db-select {
  padding: 3px 8px;
  border: 1px solid var(--joyo-border);
  border-radius: 4px;
  font-size: 12px;
  background: var(--joyo-bg-card);
}

.result-area {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.bottom-tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--joyo-border);
  flex-shrink: 0;
}

.bottom-tab {
  border: none;
  background: none;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
  color: var(--joyo-text-muted);
  border-bottom: 2px solid transparent;
}

.bottom-tab.active {
  color: var(--joyo-primary);
  border-bottom-color: var(--joyo-primary);
}

.bottom-content {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
</style>
