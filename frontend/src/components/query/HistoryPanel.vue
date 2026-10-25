<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { toast } from '@/composables/use-toast'
import { zhCN } from '@/locales/zh-CN'
import type { HistoryItem } from '@/types/ipc'

/**
 * 查询历史面板（spec-05）：关键词搜索 + 双击回填 + 单项删除 + 清空。
 * 由 QueryEditorView 以浮层形式挂在编辑器区内；数据经 window.joyo.history（Main 持久化）。
 */
const emit = defineEmits<{
  close: []
  /** 双击回填：交给父组件替换编辑器内容并聚焦 */
  fill: [sql: string]
}>()

const keyword = ref('')
const items = ref<HistoryItem[]>([])
const selectedId = ref<number | null>(null)
const loading = ref(false)

async function load(): Promise<void> {
  loading.value = true
  try {
    items.value = (await window.joyo?.history?.list({ keyword: keyword.value, limit: 50 })) ?? []
  } catch (e) {
    toast('error', e instanceof Error ? e.message : String(e))
  } finally {
    loading.value = false
  }
}

// 搜索防抖 300ms
let debounceTimer: ReturnType<typeof setTimeout> | undefined
watch(keyword, () => {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => void load(), 300)
})

let offHistoryUpdated: (() => void) | undefined

onMounted(() => {
  void load()
  // 面板打开期间执行 SQL 后实时刷新（Main 在 history:append 后广播）
  offHistoryUpdated = window.joyo?.onHistoryUpdated(() => void load())
})

onBeforeUnmount(() => {
  clearTimeout(debounceTimer)
  offHistoryUpdated?.()
})

function fill(item: HistoryItem): void {
  emit('fill', item.sql)
}

async function removeItem(item: HistoryItem): Promise<void> {
  try {
    await window.joyo?.history?.remove(item.id)
  } catch (e) {
    toast('error', e instanceof Error ? e.message : String(e))
    return
  }
  items.value = items.value.filter((i) => i.id !== item.id)
}

async function clearAll(): Promise<void> {
  try {
    await ElMessageBox.confirm(zhCN.history.clearConfirm, zhCN.history.title, { type: 'warning' })
  } catch {
    return // 用户取消
  }
  try {
    await window.joyo?.history?.clear()
    keyword.value = ''
    await load()
  } catch (e) {
    toast('error', e instanceof Error ? e.message : String(e))
  }
}

function formatTime(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 第二行摘要：连接 · 库 · 时间 · 耗时 / 行数 */
function itemMeta(item: HistoryItem): string {
  const parts = [item.connectionName, item.database, formatTime(item.executedAt)]
  const cost = item.rowCount != null
    ? `${item.elapsedMs}ms / ${zhCN.history.rowCount(item.rowCount)}`
    : `${item.elapsedMs}ms`
  parts.push(cost)
  return parts.filter((p) => p !== '').join(' · ')
}
</script>

<template>
  <div class="history-panel" data-testid="history-panel">
    <div class="panel-head">
      <el-input
        v-model="keyword"
        class="search"
        size="small"
        clearable
        :placeholder="zhCN.history.searchPlaceholder"
        data-testid="history-search"
      />
      <button class="head-btn" @click="emit('close')">✕</button>
    </div>

    <div v-if="!loading && items.length === 0" class="empty">
      {{ keyword ? zhCN.history.emptySearch : zhCN.history.empty }}
    </div>

    <div v-else class="panel-body">
      <div
        v-for="item in items"
        :key="item.id"
        class="history-item"
        :class="{ selected: item.id === selectedId, failed: !item.success }"
        :title="item.errorMessage ?? item.sql"
        data-testid="history-item"
        @click="selectedId = item.id"
        @dblclick="fill(item)"
      >
        <div class="sql">{{ item.sql }}</div>
        <div class="meta">
          <span>{{ itemMeta(item) }}</span>
          <button class="del" :title="zhCN.history.deleteOne" @click.stop="removeItem(item)">×</button>
        </div>
      </div>
    </div>

    <div class="panel-foot">
      <el-button
        size="small"
        :disabled="items.length === 0"
        data-testid="history-clear"
        @click="clearAll"
      >
        {{ zhCN.history.clear }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.history-panel {
  position: absolute;
  inset: 0 0 0 auto;
  width: 420px;
  display: flex;
  flex-direction: column;
  background: var(--joyo-bg-card);
  border-left: 1px solid var(--joyo-border);
  box-shadow: -4px 0 12px rgb(0 0 0 / 12%);
  z-index: 30;
}

.panel-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-bottom: 1px solid var(--joyo-border);
}

.search {
  flex: 1;
}

.head-btn {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--joyo-text-muted);
  font-size: 14px;
  padding: 2px 6px;
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--joyo-text-muted);
  font-size: 12px;
}

.history-item {
  padding: 6px 10px;
  border-bottom: 1px solid var(--joyo-border);
  cursor: pointer;
  border-left: 3px solid transparent;
}

.history-item:hover {
  background: var(--joyo-bg-hover, rgb(0 0 0 / 4%));
}

.history-item.selected {
  background: var(--joyo-bg-hover, rgb(0 0 0 / 4%));
  border-left-color: var(--joyo-primary);
}

.history-item.failed {
  border-left-color: var(--joyo-danger);
}

.sql {
  font-family: Consolas, monospace;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--joyo-text-muted);
  font-size: 11px;
  margin-top: 2px;
}

.del {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--joyo-text-muted);
  visibility: hidden;
}

.history-item:hover .del {
  visibility: visible;
}

.panel-foot {
  padding: 8px;
  border-top: 1px solid var(--joyo-border);
  display: flex;
  justify-content: flex-end;
}
</style>
