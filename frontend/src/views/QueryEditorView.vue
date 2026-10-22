<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import MonacoEditor from '@/components/query/MonacoEditor.vue'
import ResultPanel from '@/components/query/ResultPanel.vue'
import TableDetail from '@/components/schema/TableDetail.vue'
import LogPanel from '@/components/log/LogPanel.vue'
import { useConnectionStore } from '@/stores/connection'
import { useQueryStore } from '@/stores/query'
import { useSchemaStore } from '@/stores/schema'
import { zhCN } from '@/locales/zh-CN'

/**
 * 主区（spec-03 + F09 多页签提前实现）：
 * - 顶部页签：多个 SQL 页签（各自绑定库，执行时后端按页签 USE）+ 日志页签
 * - SQL 页签内容：工具栏/编辑器/状态条 + 下半区页签（执行结果/表结构）
 */

const connections = useConnectionStore()
const queryStore = useQueryStore()
const schemaStore = useSchemaStore()

const editorRef = ref<InstanceType<typeof MonacoEditor> | null>(null)

// ===== 编辑器 / 结果区上下拖拽分割（spec-04 第 5 节）=====

const workAreaRef = ref<HTMLDivElement | null>(null)
/** 编辑器占工作区高度百分比；默认 38%，拖拽范围 30%~75% */
const editorPercent = ref(38)

function startSplitDrag(event: MouseEvent): void {
  const root = workAreaRef.value
  if (!root) return
  event.preventDefault()
  const rect = root.getBoundingClientRect()
  const onMove = (moveEvent: MouseEvent) => {
    const percent = ((moveEvent.clientY - rect.top) / rect.height) * 100
    editorPercent.value = Math.min(75, Math.max(30, percent))
  }
  const onUp = () => {
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
    document.body.classList.remove('joyo-resizing')
  }
  document.body.classList.add('joyo-resizing')
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

const connectionId = computed(() => connections.connectedIds[0] ?? null)
const activeConn = computed(() =>
  connections.items.find((c) => c.id === connectionId.value) ?? null)

const activeTab = computed(() => queryStore.activeSqlTab)
const onSqlMainTab = computed(() => queryStore.activeMainTabId !== 'log' && activeTab.value !== null)

/** 统一渲染的页签序列（SQL 页签 + 日志，按 mainTabsOrder 排序） */
const orderedTabs = computed(() => {
  return queryStore.mainTabsOrder
    .map((id) => {
      if (id === 'log') {
        return { id, isLog: true, title: zhCN.log.entry, db: '', sqlTab: null }
      }
      const t = queryStore.sqlTabs.find((x) => x.id === id)
      if (!t) return null
      return { id, isLog: false, title: t.title, db: t.db, sqlTab: t }
    })
    .filter((x) => x !== null)
})

const dragId = ref<string | null>(null)

function onDragStart(id: string): void {
  dragId.value = id
}

function onDrop(targetId: string): void {
  if (dragId.value && dragId.value !== targetId) queryStore.moveTab(dragId.value, targetId)
  dragId.value = null
}

const schemaNames = computed(() =>
  (connectionId.value ? schemaStore.trees[connectionId.value]?.schemas : null)
    ?.map((s) => s.name) ?? [])

const activeResultTabs = computed(() => queryStore.activeResultTabs)
const activeResultId = computed(() => queryStore.activeResultId)

const draft = computed({
  get: () => (activeTab.value ? queryStore.draft(activeTab.value.id) : ''),
  set: (value: string) => {
    if (activeTab.value) queryStore.setDraft(activeTab.value.id, value)
  },
})

const bottomTab = ref<'result' | 'schema'>('result')
const selectedTable = computed(() => schemaStore.selectedTableLocation)

watch(
  () => schemaStore.selectedTableLocation,
  (loc) => {
    if (loc) bottomTab.value = 'schema'
  },
)

watch(
  () => queryStore.resultsRev,
  () => {
    bottomTab.value = 'result'
  },
)

watch(
  () => queryStore.activeMainTabId,
  () => {
    if (queryStore.activeMainTabId !== 'log') bottomTab.value = 'result'
  },
)

/** el-select 的 change 事件直接给出选中值（非 DOM Event），选中后只更新页签绑定的库 */
function onDbChange(value: string): void {
  if (activeTab.value) queryStore.setTabDb(activeTab.value.id, value)
}

function onRun(sql: string): void {
  if (!activeTab.value) return
  void queryStore.execute(activeTab.value.id, sql)
}

function onToolbarRun(): void {
  editorRef.value?.requestRun()
}

function newTab(): void {
  if (!connectionId.value) return
  queryStore.createTab(connectionId.value, activeConn.value?.defaultDatabase ?? '')
}

function closeTab(e: MouseEvent, id: string): void {
  e.stopPropagation()
  queryStore.closeSqlTab(id)
}
</script>

<template>
  <section class="main-area">
    <!-- 顶部页签：SQL 页签与日志连续排列，可拖拽换位 -->
    <div class="main-tabs">
      <button
        v-for="item in orderedTabs"
        :key="item.id"
        class="main-tab"
        :class="{ active: queryStore.activeMainTabId === item.id, 'sql-tab': !item.isLog }"
        draggable="true"
        @dragstart="onDragStart(item.id)"
        @dragover.prevent
        @drop="onDrop(item.id)"
        @click="queryStore.setActiveMainTab(item.id)"
      >
        <template v-if="item.isLog">
          <span class="tab-title">{{ item.title }}</span>
        </template>
        <template v-else>
          <span class="tab-title">{{ item.title }}</span>
          <span v-if="item.db" class="tab-db-tag">{{ item.db }}</span>
          <span class="tab-close" title="关闭" @click.stop="item.sqlTab && closeTab($event, item.sqlTab.id)">×</span>
        </template>
      </button>
      <button class="main-tab tab-add" data-testid="new-sql-tab" title="新建 SQL 页签" @click="newTab">
        ＋
      </button>
    </div>

    <!-- 日志页签：整区显示 -->
    <LogPanel v-if="queryStore.activeMainTabId === 'log'" class="log-full" />

    <!-- SQL 页签内容 -->
    <template v-else-if="onSqlMainTab && activeTab">
      <div class="toolbar">
        <span class="conn-name">{{ activeConn?.name }}</span>
        <el-select
          v-if="schemaNames.length > 0"
          class="db-select"
          data-testid="db-select"
          :model-value="activeTab.db"
          title="本页签的数据库"
          placeholder="未选择数据库"
          style="width: 160px"
          @change="onDbChange"
        >
          <el-option v-if="!activeTab.db" label="未选择数据库" value="" />
          <el-option v-for="s in schemaNames" :key="s" :label="s" :value="s" />
        </el-select>
        <span class="toolbar-sep" />
        <el-button
          type="primary"
          data-testid="run-btn"
          :disabled="queryStore.executing"
          @click="onToolbarRun"
        >
          {{ queryStore.executing ? zhCN.query.running : zhCN.query.run }}
        </el-button>
        <span class="toolbar-hint">Ctrl+Enter</span>
        <span class="spacer" />
        <label class="max-rows">
          {{ zhCN.query.maxRowsLabel }}
          <el-select v-model.number="queryStore.maxRows" style="width: 90px">
            <el-option label="100" :value="100" />
            <el-option label="1000" :value="1000" />
            <el-option label="10000" :value="10000" />
          </el-select>
        </label>
      </div>

      <div ref="workAreaRef" class="work-area">
        <div class="editor-area" :style="{ height: `${editorPercent}%` }">
          <MonacoEditor
            ref="editorRef"
            v-model="draft"
            @run="onRun"
          />
        </div>

        <div
          class="splitter"
          data-testid="splitter"
          title="拖拽调整编辑器与结果区高度"
          @mousedown="startSplitDrag"
        />

        <div class="status-bar">
          <span class="shortcut-hint">{{ zhCN.query.shortcutHint }}</span>
          <span class="spacer" />
          <span class="db-hint">{{ activeTab.db ? zhCN.query.defaultDb(activeTab.db) : zhCN.query.noDb }}</span>
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
          <ResultPanel
            v-else
            :tabs="activeResultTabs"
            :active-tab-id="activeResultId"
            :executing="queryStore.executing"
            @select="queryStore.activeResultByTab[activeTab.id] = $event"
            @page="(tab, page) => queryStore.changePage(tab, page)"
            @page-size="(tab, size) => queryStore.changePageSize(tab, size)"
          />
        </div>
        </div>
      </div>
    </template>

    <div v-else class="no-tabs-hint">{{ zhCN.query.noSqlTab }}</div>
  </section>
</template>

<style scoped>
.main-area {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.main-tabs {
  display: flex;
  align-items: center;
  gap: 2px;
  border-bottom: 1px solid var(--joyo-border);
  flex-shrink: 0;
}

.main-tab {
  border: none;
  background: none;
  padding: 7px 12px;
  font-size: 12px;
  cursor: pointer;
  color: var(--joyo-text-muted);
  border-bottom: 2px solid transparent;
  display: flex;
  align-items: center;
  gap: 6px;
}

.main-tab.active {
  color: var(--joyo-primary);
  border-bottom-color: var(--joyo-primary);
  background: var(--joyo-bg-card);
}

.sql-tab.active .tab-title {
  font-weight: 600;
}

.tab-db-tag {
  font-size: 10px;
  color: var(--joyo-text-muted);
  border: 1px solid var(--joyo-border);
  border-radius: 3px;
  padding: 0 4px;
}

.tab-close {
  font-size: 13px;
  line-height: 1;
  color: var(--joyo-text-muted);
  border-radius: 3px;
  padding: 0 2px;
}

.tab-close:hover {
  color: var(--joyo-danger);
}

.tab-add {
  font-size: 14px;
  padding: 6px 10px;
}

.spacer {
  flex: 1;
}

.log-full {
  flex: 1;
  min-height: 0;
  padding-top: 8px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 8px;
}

.conn-name {
  color: var(--joyo-text);
  font-weight: 600;
  font-size: 13px;
}

.db-select {
  padding: 3px 8px;
  border: 1px solid var(--joyo-border);
  border-radius: 4px;
  font-size: 12px;
  background: var(--joyo-bg-card);
}

.toolbar-sep {
  width: 1px;
  height: 18px;
  background: var(--joyo-border);
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

.work-area {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.editor-area {
  flex: none;
  min-height: 0;
  padding-top: 8px;
}

.splitter {
  flex: none;
  height: 7px;
  margin: 2px 0;
  position: relative;
  cursor: row-resize;
}

.splitter::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 36px;
  height: 3px;
  border-radius: 2px;
  background: var(--joyo-border);
}

.splitter:hover::after {
  background: var(--joyo-primary);
}

.status-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--joyo-text-muted);
  padding: 2px 4px;
}

.db-hint {
  color: var(--joyo-text-muted);
}

.result-area {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding-top: 4px;
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

.no-tabs-hint {
  color: var(--joyo-text-muted);
  text-align: center;
  padding: 48px 0;
  font-size: 13px;
}
</style>
