<script setup lang="ts">
import { zhCN } from '@/locales/zh-CN'

/**
 * 结果状态条（spec-04 第 2 节）：
 * 共 N 行 · 耗时 ms | 截断/隐藏列提示 | 排序仅本页 | 页码控件 | 每页行数。
 */

const props = defineProps<{
  totalRows: number
  elapsedMs: number
  truncated: boolean
  hiddenCols: number
  page: number
  pageSize: number
  totalPages: number
}>()

const emit = defineEmits<{
  page: [page: number]
  pageSize: [size: number]
}>()

/** 每页行数选项（后端上限 1000） */
const PAGE_SIZES = [50, 100, 200, 500]

function changePageSize(size: number): void {
  if (size !== props.pageSize) emit('pageSize', size)
}
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
    <label class="page-size">
      {{ zhCN.query.pageSizeLabel }}
      <el-select
        :model-value="props.pageSize"
        size="small"
        style="width: 78px"
        @change="changePageSize"
      >
        <el-option v-for="size in PAGE_SIZES" :key="size" :label="String(size)" :value="size" />
      </el-select>
    </label>
  </div>
</template>

<style scoped>
.meta-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 4px;
  font-size: 12px;
  color: var(--joyo-text-muted);
  flex: none;
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

.page-size {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
