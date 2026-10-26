<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { getLogs } from '@/api/system'
import { toast } from '@/composables/use-toast'
import { zhCN } from '@/locales/zh-CN'

/**
 * 日志视图核心组件（spec-13 第 2/4 节）：LogPanel（页签内嵌）与 LogViewerDialog（弹窗）共用。
 * - 2s 轮询 tail=200；内容签名相同不重绘；active=false 时停止轮询
 * - 跟随滚动：仅当滚动在底部时追加后滚到底；上翻暂停跟随并显示"回到底部"
 * - ERROR 行红色、WARN 行黄色；空态"暂无日志"；请求失败 toast 且保留旧内容
 */

const props = defineProps<{ embedded?: boolean; active?: boolean }>()

const REFRESH_MS = 2_000
const TAIL = 200

const lines = ref<string[]>([])
const filePath = ref('')
const sizeBytes = ref(0)
const loading = ref(false)
const offline = ref(false)
const autoRefresh = ref(true)
const autoFollow = ref(true)
const preRef = ref<HTMLDivElement | null>(null)
let timer: ReturnType<typeof setInterval> | null = null
let lastSignature = ''

const isEmpty = computed(() => sizeBytes.value === 0 && lines.value.length === 0)

async function refresh(): Promise<void> {
  loading.value = true
  try {
    const resp = await getLogs(TAIL)
    offline.value = false
    filePath.value = resp.filePath
    sizeBytes.value = resp.sizeBytes
    const signature = resp.lines.join('\n')
    // spec-13 行为 1：内容与上次相同则不重绘
    if (signature !== lastSignature) {
      lastSignature = signature
      lines.value = resp.lines
      if (autoFollow.value) await scrollToBottom()
    }
  } catch (e) {
    // spec-13：请求失败 toast 且保持旧内容
    offline.value = true
    toast('error', e instanceof Error ? e.message : String(e))
  } finally {
    loading.value = false
  }
}

async function scrollToBottom(): Promise<void> {
  await nextTick()
  const pre = preRef.value
  if (pre) {
    pre.scrollTop = pre.scrollHeight
    autoFollow.value = true
  }
}

/** spec-13 行为 2：滚离底部暂停跟随，出现"回到底部" */
function onScroll(): void {
  const pre = preRef.value
  if (!pre) return
  autoFollow.value = pre.scrollTop + pre.clientHeight >= pre.scrollHeight - 8
}

function levelClass(line: string): string {
  if (line.includes(' ERROR ')) return 'lv-error'
  if (line.includes(' WARN ')) return 'lv-warn'
  return ''
}

async function copyAll(): Promise<void> {
  try {
    await window.joyo?.copyText(lines.value.join('\n'))
    toast('success', zhCN.log.copied)
  } catch (e) {
    toast('error', e instanceof Error ? e.message : String(e))
  }
}

function openDir(): void {
  if (filePath.value) void window.joyo?.showItemInFolder(filePath.value)
}

function stopTimer(): void {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function syncTimer(): void {
  if (autoRefresh.value && !timer) timer = setInterval(() => void refresh(), REFRESH_MS)
  if (!autoRefresh.value) stopTimer()
}

watch(autoRefresh, () => syncTimer())

// active：页签可见 / 弹窗打开；关闭即停止轮询（spec-13 行为 3）
watch(
  () => props.active,
  (active) => {
    stopTimer()
    if (active) {
      void refresh()
      syncTimer()
    }
  },
)

onMounted(() => {
  if (props.active) {
    void refresh()
    syncTimer()
  }
})

onUnmounted(stopTimer)
</script>

<template>
  <div class="log-view" :class="{ embedded }" data-testid="log-view">
    <div v-if="offline" class="log-offline">{{ zhCN.log.offline }}</div>
    <div class="log-toolbar">
      <span class="log-path" :title="filePath">{{ filePath || '—' }}</span>
      <span class="spacer" />
      <label class="log-auto">
        <input v-model="autoRefresh" type="checkbox" data-testid="log-auto-refresh">
        {{ zhCN.log.autoRefresh }}
      </label>
      <span v-if="loading" class="log-loading">{{ zhCN.log.loading }}</span>
      <button class="log-btn" @click="copyAll">{{ zhCN.log.copyAll }}</button>
      <button class="log-btn" @click="openDir">{{ zhCN.log.openDir }}</button>
      <button class="log-btn" @click="refresh">{{ zhCN.log.refresh }}</button>
    </div>
    <div class="log-body">
      <div v-if="isEmpty" class="log-empty" data-testid="log-empty">{{ zhCN.log.empty }}</div>
      <div v-else ref="preRef" class="log-pre" data-testid="log-content" @scroll="onScroll">
        <div v-for="(line, i) in lines" :key="i" class="log-line" :class="levelClass(line)">{{ line || ' ' }}</div>
      </div>
      <button v-if="!autoFollow" class="log-back" data-testid="log-back-bottom" @click="scrollToBottom">
        {{ zhCN.log.backToBottom }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.log-view {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.log-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 8px;
  flex-shrink: 0;
}

.log-path {
  font-size: 11px;
  color: var(--joyo-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 460px;
}

.spacer {
  flex: 1;
}

.log-auto {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--joyo-text-muted);
  cursor: pointer;
  white-space: nowrap;
}

.log-loading {
  font-size: 11px;
  color: var(--joyo-text-muted);
}

.log-btn {
  padding: 4px 12px;
  border: 1px solid var(--joyo-border);
  border-radius: 6px;
  background: var(--joyo-bg-card);
  cursor: pointer;
  font-size: 12px;
  white-space: nowrap;
}

.log-btn:hover {
  border-color: var(--joyo-primary);
}

.log-offline {
  padding: 4px 10px;
  margin-bottom: 6px;
  border-radius: 6px;
  background: var(--joyo-warning-bg, #fdf6ec);
  color: #b8860b;
  font-size: 12px;
  flex-shrink: 0;
}

.log-body {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
}

.log-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--joyo-text-muted);
  font-size: 13px;
  border: 1px dashed var(--joyo-border);
  border-radius: 6px;
}

/* spec-13：等宽字体、横向滚动不折行 */
.log-pre {
  flex: 1;
  margin: 0;
  padding: 12px;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 6px;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
  overflow: auto;
  white-space: pre;
}

.log-line {
  min-height: 1.5em;
}

.log-line.lv-error {
  color: #f56c6c;
}

.log-line.lv-warn {
  color: #e6a23c;
}

.log-back {
  position: absolute;
  right: 16px;
  bottom: 16px;
  padding: 5px 14px;
  border: 1px solid var(--joyo-border);
  border-radius: 999px;
  background: var(--joyo-bg-card);
  cursor: pointer;
  font-size: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.log-back:hover {
  border-color: var(--joyo-primary);
  color: var(--joyo-primary);
}
</style>
