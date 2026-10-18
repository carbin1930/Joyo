<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch, nextTick } from 'vue'
import BaseDialog from '@/components/base/BaseDialog.vue'
import { getLogs } from '@/api/system'
import { zhCN } from '@/locales/zh-CN'

/**
 * 后端日志查看弹窗（F13 精简版）：尾部 200 行，手动刷新 + 打开期间每 3 秒自动刷新。
 */

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

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

watch(
  () => props.open,
  (open) => {
    if (open) {
      void refresh()
      timer = setInterval(() => void refresh(), 3000)
    } else {
      stopTimer()
    }
  },
)

onMounted(() => {
  if (props.open) void refresh()
})

onUnmounted(stopTimer)
</script>

<template>
  <BaseDialog :open="open" :title="zhCN.log.title" :width="860" @close="emit('close')">
    <div class="log-toolbar">
      <span class="log-path" :title="logPath">{{ logPath }}</span>
      <span class="spacer" />
      <span v-if="loading" class="log-loading">{{ zhCN.log.loading }}</span>
      <button class="log-refresh" @click="refresh">{{ zhCN.log.refresh }}</button>
    </div>
    <pre ref="preRef" class="log-pre" data-testid="log-content">{{ lines.join('\n') }}</pre>
  </BaseDialog>
</template>

<style scoped>
.log-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.log-path {
  font-size: 11px;
  color: var(--joyo-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 560px;
}

.spacer {
  flex: 1;
}

.log-loading {
  font-size: 11px;
  color: var(--joyo-text-muted);
}

.log-refresh {
  padding: 4px 12px;
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
  height: 60vh;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
