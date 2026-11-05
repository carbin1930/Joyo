<script setup lang="ts">
import { zhCN } from '@/locales/zh-CN'

/**
 * 结果状态条（spec-04 第 2 节）：
 * 共 N 行 · 耗时 ms | 截断/隐藏列提示 | 排序仅本页 | 页码控件。
 * 每页行数固定 200（2026-11-06 移除下拉框：与"行数限制"职责重叠，收益低且易混淆）。
 */

const props = defineProps<{
  totalRows: number
  elapsedMs: number
  truncated: boolean
  hiddenCols: number
  page: number
  totalPages: number
}>()

const emit = defineEmits<{
  page: [page: number]
}>()
</script>

<template>
  <div class="meta-bar" data-testid="result-status-bar">
    <span>{{ zhCN.query.rowsInfo(props.totalRows, props.elapsedMs) }}</span>
    <span v-if="props.truncated" class="truncated-badge">
      {{ zhCN.query.truncatedTo(props.totalRows) }}
    </span>
    <span v-if="props.hiddenCols > 0" class="truncated-badge" data-testid="hidden-cols">
      {{ zhCN.query.hiddenColumns(props.hiddenCols) }}
    </span>
    <span class="spacer" />
    <span class="sort-hint">{{ zhCN.query.sortLocalOnly }}</span>
    <span class="pager">
      <button class="page-btn" :disabled="props.page <= 1" @click="emit('page', props.page - 1)">
        ◀
      </button>
      <span>{{ zhCN.query.pageInfo(props.page, props.totalPages) }}</span>
      <button
        class="page-btn"
        :disabled="props.page >= props.totalPages"
        @click="emit('page', props.page + 1)"
      >
        ▶
      </button>
    </span>
  </div>
</template>

<style scoped>
.meta-bar {
  display: flex;
  align-items: center;
  gap: var(--joyo-space-3);
  padding: 0 var(--joyo-space-3);
  height: 28px;
  font-size: var(--joyo-fs-sm);
  color: var(--joyo-text-muted);
  border-bottom: 1px solid var(--joyo-border);
  background: var(--joyo-bg-card);
  flex: none;
}

.truncated-badge {
  color: var(--joyo-warning);
  background: rgb(226 160 63 / 12%);
  border: 1px solid rgb(226 160 63 / 45%);
  border-radius: var(--joyo-radius-sm);
  padding: 0 6px;
  line-height: 16px;
}

.spacer {
  flex: 1;
}

.sort-hint {
  color: var(--joyo-text-muted);
  font-size: 11px;
}

.pager {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-btn {
  height: var(--joyo-h-control-sm);
  min-width: 26px;
  border: 1px solid var(--joyo-border);
  background: var(--joyo-bg-card);
  color: var(--joyo-text);
  border-radius: var(--joyo-radius-sm);
  cursor: pointer;
  padding: 0 6px;
  font-size: var(--joyo-fs-sm);
  transition:
    background-color var(--joyo-transition),
    border-color var(--joyo-transition);
}

.page-btn:hover:not(:disabled) {
  border-color: var(--joyo-primary);
  color: var(--joyo-primary);
  background: var(--joyo-primary-weak);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
