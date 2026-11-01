<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useBackendStore } from '@/stores/backend'
import { useConnectionStore } from '@/stores/connection'
import { resetApiConfig } from '@/api/http'
import QueryEditorView from '@/views/QueryEditorView.vue'
import { zhCN } from '@/locales/zh-CN'

const backend = useBackendStore()
const connections = useConnectionStore()
const restarting = ref(false)

/** 已连接 → SQL 编辑器常驻主区（表结构在下半区页签）；否则首页状态卡 */
const connected = computed(() => connections.connectedIds.length > 0)

onMounted(() => {
  void backend.check()
})

/** PRD 可靠性要求：后端进程异常退出后可直接重启后端（Main 负责 stop → 重新拉起） */
async function restartBackend(): Promise<void> {
  if (restarting.value) return
  restarting.value = true
  try {
    await window.joyo?.restartBackend()
    // 后端换了端口与 token，渲染层缓存的连接配置必须重置
    resetApiConfig()
    await backend.check()
  } catch (e) {
    backend.errorMessage = e instanceof Error ? e.message : String(e)
  } finally {
    restarting.value = false
  }
}
</script>

<template>
  <section class="home">
    <QueryEditorView v-if="connected" />
    <template v-else>
      <h1 class="home-title">{{ zhCN.app.name }}</h1>
      <p class="home-subtitle">{{ zhCN.app.slogan }}</p>

      <div class="status-card" :class="`status-${backend.status}`" data-testid="backend-status">
        <span class="status-dot" />
        <span>{{ backend.status === 'ok' ? zhCN.backend.ok(backend.info?.version ?? '') : backend.status === 'unavailable' ? zhCN.backend.unavailable : zhCN.backend.checking }}</span>
        <span v-if="backend.status === 'unavailable'" class="status-error">
          {{ backend.errorMessage }}
        </span>
        <button v-if="backend.status === 'unavailable'" class="retry-btn" @click="backend.check()">
          {{ zhCN.backend.retry }}
        </button>
        <button
          v-if="backend.status === 'unavailable'"
          class="retry-btn"
          data-testid="restart-backend"
          :disabled="restarting"
          @click="restartBackend"
        >
          {{ restarting ? zhCN.backend.restarting : zhCN.backend.restart }}
        </button>
      </div>

      <p class="hint">{{ zhCN.schema.hint }}</p>
    </template>
  </section>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.home-title {
  font-size: 24px;
  margin: 8px 0 4px;
}

.home-subtitle {
  color: var(--joyo-text-muted);
  margin: 0 0 24px;
}

.status-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid var(--joyo-border);
  border-radius: 8px;
  max-width: 520px;
  font-size: 14px;
}

.status-ok {
  border-color: var(--joyo-success);
}

.status-unavailable {
  border-color: var(--joyo-danger);
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-ok .status-dot {
  background: var(--joyo-success);
}

.status-unavailable .status-dot {
  background: var(--joyo-danger);
}

.status-checking .status-dot {
  background: var(--joyo-text-muted);
}

.status-error {
  color: var(--joyo-danger);
  font-size: 13px;
}

.retry-btn {
  margin-left: auto;
  padding: 4px 12px;
  border: 1px solid var(--joyo-border);
  border-radius: 6px;
  background: var(--joyo-bg-card);
  cursor: pointer;
}

.retry-btn:hover {
  border-color: var(--joyo-primary);
}

.hint {
  margin-top: 20px;
  color: var(--joyo-text-muted);
  font-size: 13px;
}
</style>
