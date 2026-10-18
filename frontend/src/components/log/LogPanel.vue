<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch, nextTick } from 'vue'
import { getLogs } from '@/api/system'
import { zhCN } from '@/locales/zh-CN'

/**
 * 后端日志面板（F13 精简版）：下半区"日志"页签内容。
 * 尾部 200 行，自动滚动到底，页签可见期间每 3 秒自动刷新。
 */

const lines = ref<string[]>([])
const logPath = ref('')
const loading = ref(false)
const preRef = ref<HTMLPreElement | null>(null)
let timer: ReturnType<typeof setInterval> | null = null

async function refresh(): Promise<void> {
  loading.value = true
  try {
    const resp = await getLogs(200)
    lines.value = resp.lines
    logPath.value = resp.path
    await nextTick()
    if (preRef.value) preRef.value.scrollTop = preRef.value.scrollHeight
  } catch (e) {
    lines.value = [String(e instanceof Error ? e.message : e)]
  } finally {
    loading.value = false
  }
}

function stopTimer(): void {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

onMounted(() => {
  void refresh()
  timer = setInterval(() => void refresh(), 3000)
})

onUnmounted(stopTimer)

// 组件被 v-if 切换重建时也保持刷新
watch(
  () => lines.value.length,
  async () => {
    await nextTick()
    if (preRef.value) preRef.value.scrollTop = preRef.value.scrollHeight
  },
)
</script>

<template>
  <div class="log-panel" data-testid="log-panel">
    <div class="log-toolbar">
      <span class="log-path" :title="logPath">{{ logPath }}</span>
      <span class="spacer" />
      <span v-if="loading" class="log-loading">{{ zhCN.log.loading }}</span>
      <el-button size="small" :loading="loading" @click="refresh">{{ zhCN.log.refresh }}</el-button>
    </div>
    <pre ref="preRef" class="log-pre" data-testid="log-content">{{ lines.join('\n') }}</pre>
  </div>
</template>

<style scoped>
.log-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.log-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 6px;
  flex-shrink: 0;
}

.log-path {
  font-size: 11px;
  color: var(--joyo-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.spacer {
  flex: 1;
}

.log-loading {
  font-size: 11px;
  color: var(--joyo-text-muted);
}

.log-refresh {
  padding: 3px 10px;
  border: 1px solid var(--joyo-border);
  border-radius: 6px;
  background: var(--joyo-bg-card);
  cursor: pointer;
  font-size: 12px;
}

.log-refresh:hover {
  border-color: var(--joyo-primary);
}

.log-pre {
  margin: 0;
  padding: 12px;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 6px;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
  flex: 1;
  min-height: 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
