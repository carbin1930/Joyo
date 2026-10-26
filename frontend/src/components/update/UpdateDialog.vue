<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import BaseDialog from '@/components/base/BaseDialog.vue'
import type { UpdateState } from '@/types/ipc'
import { zhCN } from '@/locales/zh-CN'

/**
 * 更新提示（spec-19/F11）：检查中 / 无更新 / 有更新 / 下载进度 / 完成 / 失败。
 * 状态来自 Main 推送（updater:event），打开时主动检查一次。
 */

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

const state = ref<UpdateState>({ phase: 'idle' })
const installing = ref(false)
let unsubscribe: (() => void) | null = null

onMounted(() => {
  unsubscribe = window.joyo?.onUpdateEvent((next) => {
    state.value = next
  }) ?? null
})

onUnmounted(() => unsubscribe?.())

watch(
  () => props.open,
  (open) => {
    if (open) void check()
  },
)

async function check(): Promise<void> {
  state.value = { phase: 'checking' }
  try {
    state.value = (await window.joyo?.checkUpdate()) ?? { phase: 'unsupported' }
  } catch (e) {
    state.value = { phase: 'error', message: e instanceof Error ? e.message : String(e) }
  }
}

async function install(): Promise<void> {
  installing.value = true
  try {
    await window.joyo?.installUpdate()
  } finally {
    installing.value = false
  }
}

const statusText = computed(() => {
  const s = state.value
  switch (s.phase) {
    case 'checking':
      return zhCN.updater.checking
    case 'not-available':
      return zhCN.updater.upToDate
    case 'available':
      return zhCN.updater.newVersion(s.version ?? '')
    case 'downloading':
      return zhCN.updater.downloading(s.percent ?? 0)
    case 'downloaded':
      return zhCN.updater.ready(s.version ?? '')
    case 'unsupported':
      return s.message ?? zhCN.updater.unsupported
    case 'error':
      return s.message ?? zhCN.updater.failed
    default:
      return zhCN.updater.idle
  }
})
</script>

<template>
  <BaseDialog :open="props.open" :title="zhCN.updater.title" :width="420" @close="emit('close')">
    <div class="update" data-testid="update-dialog">
      <div class="status" data-testid="update-status">{{ statusText }}</div>

      <div v-if="state.phase === 'downloading'" class="progress">
        <div class="bar" :style="{ width: `${state.percent ?? 0}%` }" />
      </div>

      <div v-if="state.notes" class="notes" data-testid="update-notes">{{ state.notes }}</div>

      <div class="actions">
        <el-button size="small" @click="emit('close')">{{ zhCN.common.cancel }}</el-button>
        <el-button
          v-if="state.phase === 'error' || state.phase === 'not-available' || state.phase === 'unsupported'"
          size="small"
          @click="check"
        >
          {{ zhCN.updater.retry }}
        </el-button>
        <el-button
          v-if="state.phase === 'available'"
          size="small"
          type="primary"
          @click="check"
        >
          {{ zhCN.updater.download }}
        </el-button>
        <el-button
          v-if="state.phase === 'downloaded'"
          size="small"
          type="danger"
          data-testid="update-install"
          :loading="installing"
          @click="install"
        >
          {{ zhCN.updater.install }}
        </el-button>
      </div>
    </div>
  </BaseDialog>
</template>

<style scoped>
.update {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.status {
  font-size: 13px;
  color: var(--joyo-text);
}

.progress {
  height: 6px;
  border-radius: 3px;
  background: var(--joyo-bg-sidebar);
  overflow: hidden;
}

.bar {
  height: 100%;
  background: var(--joyo-primary);
  transition: width 200ms ease;
}

.notes {
  max-height: 160px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  color: var(--joyo-text-muted);
  white-space: pre-wrap;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
