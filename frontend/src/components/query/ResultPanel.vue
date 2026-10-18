<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQueryStore, type ResultTab } from '@/stores/query'
import { zhCN } from '@/locales/zh-CN'

/**
 * 结果面板（spec-03 第 2 节）：多语句 → 多 Tab；SELECT 渲染基础表格 + 分页；
 * UPDATE/DDL 显示影响行数消息；错误 Tab 显示错误面板。
 * 完整网格能力（NULL 样式/复制 CSV）由 spec-04 的 ResultGrid 升级。
 */

const queryStore = useQueryStore()
const activeTab = computed(() =>
  queryStore.tabs.find((t) => t.id === queryStore.activeTabId) ?? null)

const pageInput = ref(1)
watch(
  () => queryStore.activeTabId,
  () => {
    pageInput.value = activeTab.value?.statement?.page ?? 1
  },
)

function totalPages(tab: ResultTab): number {
  const stmt = tab.statement
  if (!stmt?.pageSize || stmt.totalRows == null) return 1
  return Math.max(1, Math.ceil(stmt.totalRows / stmt.pageSize))
}

function changePage(tab: ResultTab, page: number): void {
  void queryStore.changePage(tab, page)
  pageInput.value = page
}
</script>

<template>
  <div class="result-panel" data-testid="result-panel">
    <div v-if="queryStore.tabs.length === 0 && !queryStore.executing" class="panel-empty">
      {{ zhCN.query.noResult }}
    </div>
    <div v-else-if="queryStore.executing" class="panel-empty">{{ zhCN.query.running }}</div>

    <template v-else>
      <div class="result-tabs">
        <button
          v-for="tab in queryStore.tabs"
          :key="tab.id"
          class="result-tab"
          :class="[
            { active: tab.id === queryStore.activeTabId },
            tab.kind === 'ERROR' ? 'tab-error' : tab.kind === 'SELECT' ? 'tab-select' : 'tab-msg',
          ]"
          @click="queryStore.activeTabId = tab.id"
        >
          {{ tab.title }}
        </button>
      </div>

      <div v-if="activeTab" class="tab-content">
        <!-- 错误 Tab -->
        <div v-if="activeTab.kind === 'ERROR'" class="error-block" data-testid="error-block">
          {{ activeTab.errorMessage }}
        </div>

        <!-- SELECT 结果 -->
        <template v-else-if="activeTab.kind === 'SELECT' && activeTab.statement">
          <div class="meta-bar">
            <span>{{ zhCN.query.rowsInfo(activeTab.statement.totalRows ?? 0, activeTab.statement.elapsedMs) }}</span>
            <span v-if="activeTab.statement.truncated" class="truncated-badge">
              {{ zhCN.query.truncated }}
            </span>
            <span class="spacer" />
            <span class="pager">
              <button
                class="page-btn"
                :disabled="(activeTab.statement.page ?? 1) <= 1"
                @click="changePage(activeTab, (activeTab.statement.page ?? 1) - 1)"
              >
                ◀
              </button>
              <span>{{ zhCN.query.pageInfo(activeTab.statement.page ?? 1, totalPages(activeTab)) }}</span>
              <button
                class="page-btn"
                :disabled="(activeTab.statement.page ?? 1) >= totalPages(activeTab)"
                @click="changePage(activeTab, (activeTab.statement.page ?? 1) + 1)"
              >
                ▶
              </button>
            </span>
          </div>
          <div class="grid-wrap">
            <table class="grid">
              <thead>
                <tr>
                  <th class="rownum">#</th>
                  <th v-for="col in activeTab.statement.columns" :key="col.name" :title="col.type">
                    {{ col.name }}
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, ri) in activeTab.statement.rows" :key="ri">
                  <td class="rownum">{{ ((activeTab.statement.page ?? 1) - 1) * (activeTab.statement.pageSize ?? 0) + ri + 1 }}</td>
                  <td v-for="(cell, ci) in row" :key="ci" :class="{ 'cell-null': cell === null }">
                    <template v-if="cell === null">NULL</template>
                    <template v-else>{{ cell }}</template>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="activeTab.statement.rows?.length === 0" class="grid-empty">
              {{ zhCN.query.zeroRows }}
            </div>
          </div>
        </template>

        <!-- DML/DDL 消息 -->
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

.meta-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 4px;
  font-size: 12px;
  color: var(--joyo-text-muted);
}

.truncated-badge {
  color: var(--joyo-warning);
  border: 1px solid var(--joyo-warning);
  border-radius: 4px;
  padding: 1px 6px;
}

.spacer {
  flex: 1;
}

.pager {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-btn {
  border: 1px solid var(--joyo-border);
  background: var(--joyo-bg-card);
  border-radius: 4px;
  cursor: pointer;
  padding: 2px 8px;
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.grid-wrap {
  flex: 1;
  overflow: auto;
  border: 1px solid var(--joyo-border);
  border-radius: 6px;
}

.grid {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  font-family: Consolas, monospace;
}

.grid th {
  text-align: left;
  padding: 6px 10px;
  background: var(--joyo-bg-sidebar);
  border-bottom: 1px solid var(--joyo-border);
  position: sticky;
  top: 0;
}

.grid td {
  padding: 5px 10px;
  border-bottom: 1px solid var(--joyo-border);
  white-space: nowrap;
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rownum {
  color: var(--joyo-text-muted);
  width: 48px;
}

.cell-null {
  color: var(--joyo-text-muted);
  font-style: italic;
}

.grid-empty {
  padding: 16px;
  text-align: center;
  color: var(--joyo-text-muted);
  font-size: 12px;
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
