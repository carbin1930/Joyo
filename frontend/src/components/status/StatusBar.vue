<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useBackendStore } from '@/stores/backend'
import SettingsDialog from '@/components/settings/SettingsDialog.vue'
import type { MetricsTick } from '@/types/api'
import { zhCN } from '@/locales/zh-CN'

/**
 * 全局状态栏（spec-14 第 2 节，高 24px 所有页面可见）：
 * 数据来自 Main 每 3s 推送的 metrics:tick，渲染层只接收不轮询；
 * 数值不变时 Vue patch 不产生 DOM 更新，避免高频重绘（spec 行为 3）。
 */

const backend = useBackendStore()
const tick = ref<MetricsTick | null>(null)
let unsubscribe: (() => void) | null = null

/** spec-18：设置入口（全局可见） */
const settingsOpen = ref(false)

onMounted(() => {
  unsubscribe = window.joyo?.onMetrics((t) => {
    tick.value = t
  }) ?? null
})

onUnmounted(() => unsubscribe?.())

/** 后端在线：优先 Main 采样结论；首帧未到时回退渲染层心跳 */
const backendUp = computed(() => {
  if (tick.value) return tick.value.backendUp
  return backend.status === 'ok'
})

const versionText = computed(() => {
  const v = backend.info?.version
  return v ? `v${v}` : ''
})

const electronText = computed(() => {
  const e = tick.value?.electron
  if (!e) return ''
  return `Electron ${e.processCount}进程 · ${e.memMb}MB · CPU ${e.cpuPercent.toFixed(1)}%`
})

/** JVM 段：离线/无数据灰显"后端离线"；cpuLoad 首次采样显示"--" */
const jvmText = computed(() => {
  if (!tick.value?.backendUp || !tick.value.jvm) return zhCN.status.backendOffline
  const jvm = tick.value.jvm
  const used = jvm.heapUsedMb == null ? '--' : Math.round(jvm.heapUsedMb)
  const max = jvm.heapMaxMb == null ? '--' : Math.round(jvm.heapMaxMb)
  const load = jvm.cpuLoad == null ? '--' : `${(jvm.cpuLoad * 100).toFixed(1)}%`
  return `JVM ${used}/${max}MB · CPU ${load}`
})

const jvmMuted = computed(() => !tick.value?.backendUp || !tick.value.jvm)

const tooltip = computed(() => {
  const lines = [zhCN.status.tooltipBackend]
  if (tick.value) {
    lines.push(electronText.value)
    lines.push(jvmText.value)
  }
  return lines.join('\n')
})
</script>

<template>
  <footer class="status-bar" data-testid="status-bar" :title="tooltip">
    <div class="sb-left">
      <span class="sb-dot" :class="{ on: backendUp }" data-testid="backend-dot" />
      <span>Joyo {{ versionText }}</span>
    </div>
    <div class="sb-right">
      <span>{{ electronText || '—' }}</span>
      <span class="sb-divider">|</span>
      <span :class="{ muted: jvmMuted }" data-testid="jvm-metrics">{{ jvmText }}</span>
      <button
        class="sb-gear"
        data-testid="open-settings"
        :title="zhCN.settings.open"
        @click="settingsOpen = true"
      >
        ⚙
      </button>
    </div>

    <SettingsDialog :open="settingsOpen" @close="settingsOpen = false" />
  </footer>
</template>

<style scoped>
.status-bar {
  height: 24px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  border-top: 1px solid var(--joyo-border);
  background: var(--joyo-bg-sidebar);
  font-size: 11px;
  color: var(--joyo-text-muted);
  user-select: none;
}

.sb-left {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sb-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0c4cc;
}

.sb-dot.on {
  background: #67c23a;
}

.sb-right {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.sb-divider {
  color: var(--joyo-border);
}

.muted {
  opacity: 0.55;
}

.sb-gear {
  border: none;
  background: none;
  padding: 0 2px;
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
  color: var(--joyo-text-muted);
}

.sb-gear:hover {
  color: var(--joyo-primary);
}
</style>
