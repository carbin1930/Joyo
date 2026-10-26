<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ResultContextMenu from '@/components/result/ResultContextMenu.vue'
import ResultGrid from '@/components/result/ResultGrid.vue'
import ResultStatusBar from '@/components/result/ResultStatusBar.vue'
import ResultEditToolbar from '@/components/result/ResultEditToolbar.vue'
import SaveChangesDialog from '@/components/result/SaveChangesDialog.vue'
import { hiddenColumnCount } from '@/composables/use-result-grid'
import {
  isDirty,
  previewStatements,
  summarize,
  type ChangeSummary,
} from '@/composables/use-result-edit'
import { toast } from '@/composables/use-toast'
import { zhCN } from '@/locales/zh-CN'
import { useQueryStore, type ResultTab } from '@/stores/query'

/**
 * 结果面板（展示层）：多语句 → 多 Tab；SELECT 网格 + 分页；DML/DDL 消息；错误面板；
 * spec-16：SELECT 结果附带编辑工具栏与保存确认弹窗（仅可编辑结果出现）。
 * 状态由父组件（QueryEditorView）按活动 SQL 页签传入。
 */

const props = defineProps<{
  tabs: ResultTab[]
  activeTabId: string | null
  executing: boolean
}>()

const emit = defineEmits<{
  select: [tabId: string]
  page: [tab: ResultTab, page: number]
  pageSize: [tab: ResultTab, size: number]
}>()

const activeTab = computed(() => props.tabs.find((t) => t.id === props.activeTabId) ?? null)

/** 超出渲染上限的列数（spec-04 第 5 节） */
const hiddenCols = computed(() => hiddenColumnCount(activeTab.value?.statement?.columns ?? []))

// ===== 右键菜单与复制（spec-04 第 4 节）=====

const gridRef = ref<InstanceType<typeof ResultGrid> | null>(null)
const menu = ref<{ x: number; y: number; hasSelection: boolean } | null>(null)

function onGridContext(payload: { x: number; y: number; hasSelection: boolean }): void {
  menu.value = payload
}

function closeMenu(): void {
  menu.value = null
}

/** 有选区复制选区，否则复制当前页整页 */
function copyViaMenu(format: 'csv' | 'tsv'): void {
  const scope = menu.value?.hasSelection ? 'selection' : 'all'
  closeMenu()
  void gridRef.value?.copy(format, scope)
}

function onWindowKey(event: KeyboardEvent): void {
  if (event.key === 'Escape') closeMenu()
}

onMounted(() => {
  window.addEventListener('click', closeMenu)
  window.addEventListener('keydown', onWindowKey)
})

onBeforeUnmount(() => {
  window.removeEventListener('click', closeMenu)
  window.removeEventListener('keydown', onWindowKey)
})

watch(() => props.activeTabId, closeMenu)

/** 状态条只关心页码/每页行数，结果页签由面板自己定位 */
function onStatusPage(page: number): void {
  if (activeTab.value) emit('page', activeTab.value, page)
}

function onStatusPageSize(size: number): void {
  if (activeTab.value) emit('pageSize', activeTab.value, size)
}

// ===== 表数据编辑（spec-16） =====

const store = useQueryStore()

const editable = computed(() => activeTab.value?.statement?.editable ?? null)
const editState = computed(() => (activeTab.value ? store.editStateOf(activeTab.value.id) : null))
const dirty = computed(() => (editState.value ? isDirty(editState.value) : false))
const summary = computed<ChangeSummary>(() =>
  editState.value ? summarize(editState.value) : { insert: 0, update: 0, delete: 0 },
)
const statements = computed(() =>
  editState.value && editable.value ? previewStatements(editState.value, editable.value) : [],
)

const hasSelection = ref(false)
const saveOpen = ref(false)

function onSelection(has: boolean): void {
  hasSelection.value = has
}

function onCellEdit(payload: {
  rowIndex: number
  column: string
  value: string | null
  original: string | null
  pk: Record<string, string | null>
}): void {
  if (!activeTab.value) return
  store.recordCell(activeTab.value.id, payload)
}

function onDeleteRows(): void {
  const tab = activeTab.value
  if (!tab) return
  const indexes = gridRef.value?.selectedRowIndexes() ?? []
  if (indexes.length === 0) {
    toast('info', zhCN.edit.selectRowFirst)
    return
  }
  for (const rowIndex of indexes) {
    store.markRowDeleted(tab.id, rowIndex, gridRef.value?.pkOfRow(rowIndex) ?? undefined)
  }
}

function onConfirmSave(): void {
  const tab = activeTab.value
  if (!tab) return
  saveOpen.value = false
  void store.saveEdits(tab.id)
}

watch(() => props.activeTabId, () => {
  saveOpen.value = false
  hasSelection.value = false
})

/** 总页数（页大小变化后由状态条重新计算展示） */
function totalPages(tab: ResultTab): number {
  const stmt = tab.statement
  if (!stmt?.pageSize || stmt.totalRows == null) return 1
  return Math.max(1, Math.ceil(stmt.totalRows / stmt.pageSize))
}
</script>

<template>
  <div class="result-panel" data-testid="result-panel">
    <div v-if="props.tabs.length === 0 && !props.executing" class="panel-empty">
      {{ zhCN.query.noResult }}
    </div>
    <div v-else-if="props.executing" class="panel-empty">{{ zhCN.query.running }}</div>

    <template v-else>
      <div class="result-tabs">
        <button
          v-for="tab in props.tabs"
          :key="tab.id"
          class="result-tab"
          :class="[
            { active: tab.id === props.activeTabId },
            tab.kind === 'ERROR' ? 'tab-error' : tab.kind === 'SELECT' ? 'tab-select' : 'tab-msg',
          ]"
          @click="emit('select', tab.id)"
        >
          {{ tab.title }}
        </button>
      </div>

      <div v-if="activeTab" class="tab-content">
        <div v-if="activeTab.kind === 'ERROR'" class="error-block" data-testid="error-block">
          {{ activeTab.errorMessage }}
        </div>

        <template v-else-if="activeTab.kind === 'SELECT' && activeTab.statement">
          <ResultStatusBar
            :total-rows="activeTab.statement.totalRows ?? 0"
            :elapsed-ms="activeTab.statement.elapsedMs"
            :truncated="activeTab.statement.truncated ?? false"
            :hidden-cols="hiddenCols"
            :page="activeTab.statement.page ?? 1"
            :page-size="activeTab.statement.pageSize ?? 100"
            :total-pages="totalPages(activeTab)"
            @page="onStatusPage"
            @page-size="onStatusPageSize"
          />
          <!-- spec-16：仅 SELECT 结果出现编辑工具栏（不可编辑时显示原因） -->
          <ResultEditToolbar
            :editable="editable"
            :unlocked="editState?.unlocked ?? false"
            :summary="summary"
            :dirty="dirty"
            :saving="store.savingEdit"
            :has-selection="hasSelection"
            @toggle="activeTab && store.toggleEdit(activeTab.id)"
            @add-row="activeTab && store.addEditRow(activeTab.id)"
            @delete-row="onDeleteRows"
            @save="saveOpen = true"
            @discard="activeTab && store.discardEdits(activeTab.id)"
          />
          <div class="grid-wrap">
            <ResultGrid
              ref="gridRef"
              :columns="activeTab.statement.columns ?? []"
              :rows="activeTab.statement.rows ?? []"
              :page="activeTab.statement.page ?? 1"
              :page-size="activeTab.statement.pageSize ?? 100"
              :editable="editable"
              :unlocked="editState?.unlocked ?? false"
              :changes="editState?.changes ?? {}"
              @context="onGridContext"
              @selection="onSelection"
              @edit="onCellEdit"
            />
          </div>
        </template>

        <div v-else-if="activeTab.statement" class="msg-block" data-testid="msg-block">
          {{
            zhCN.query.affectedInfo(
              activeTab.statement.kind,
              activeTab.statement.affectedRows ?? 0,
              activeTab.statement.elapsedMs,
            )
          }}
        </div>
      </div>
    </template>

    <!-- 右键菜单：有选区复制选区，否则复制当前页整页 -->
    <ResultContextMenu
      v-if="menu"
      :x="menu.x"
      :y="menu.y"
      @copy="copyViaMenu"
    />

    <!-- spec-16：保存改动确认弹窗 -->
    <SaveChangesDialog
      :open="saveOpen"
      :summary="summary"
      :statements="statements"
      :saving="store.savingEdit"
      @close="saveOpen = false"
      @confirm="onConfirmSave"
    />
  </div>
</template>

<style scoped>
.result-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
}

.panel-empty {
  color: var(--joyo-text-muted);
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
}

.result-tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--joyo-border);
  flex-shrink: 0;
}

.result-tab {
  border: none;
  background: none;
  padding: 7px 12px;
  font-size: 12px;
  cursor: pointer;
  color: var(--joyo-text-muted);
  border-bottom: 2px solid transparent;
}

.result-tab.active {
  color: var(--joyo-primary);
  border-bottom-color: var(--joyo-primary);
}

.tab-error {
  color: var(--joyo-danger) !important;
}

.tab-content {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.error-block {
  margin: 12px;
  padding: 12px 14px;
  border: 1px solid var(--joyo-danger);
  border-radius: 8px;
  color: var(--joyo-danger);
  font-family: Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap;
}

.grid-wrap {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.msg-block {
  margin: 12px;
  padding: 12px 14px;
  border: 1px solid var(--joyo-success);
  border-radius: 8px;
  color: var(--joyo-success);
  font-size: 13px;
}


</style>
