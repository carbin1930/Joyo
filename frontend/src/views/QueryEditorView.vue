<script setup lang="ts">
import { computed, ref } from 'vue'
import MonacoEditor from '@/components/query/MonacoEditor.vue'
import ResultPanel from '@/components/query/ResultPanel.vue'
import { useConnectionStore } from '@/stores/connection'
import { useQueryStore } from '@/stores/query'
import { zhCN } from '@/locales/zh-CN'

/**
 * SQL 编辑器视图（spec-03 第 2 节）：上半编辑器 + 下半结果面板。
 * 目标连接 = 对象树激活的连接（未连接时组件不渲染）。
 */

const connections = useConnectionStore()
const queryStore = useQueryStore()

const editorRef = ref<InstanceType<typeof MonacoEditor> | null>(null)

const connectionId = computed(() => connections.connectedIds[0] ?? null)
const activeConn = computed(() =>
  connections.items.find((c) => c.id === connectionId.value) ?? null)

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
        @run="onRun"
      />
    </div>

    <div class="status-bar">
      <span class="conn-name">{{ activeConn?.name }}</span>
      <span v-if="activeConn?.defaultDatabase" class="db-name">
        {{ zhCN.query.defaultDb(activeConn.defaultDatabase) }}
      </span>
      <span class="spacer" />
      <span class="shortcut-hint">{{ zhCN.query.shortcutHint }}</span>
    </div>

    <div class="result-area">
      <ResultPanel />
    </div>
  </section>
</template>

<style scoped>
.editor-view {
  display: flex;
  flex-direction: column;
  height: 100%;
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
  height: 40%;
  min-height: 160px;
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

.result-area {
  flex: 1;
  min-height: 0;
}
</style>
