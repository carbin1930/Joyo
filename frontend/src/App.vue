<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useBackendStore } from '@/stores/backend'
import { useConnectionStore } from '@/stores/connection'
import { useQueryStore } from '@/stores/query'
import ConnectionList from '@/components/connection/ConnectionList.vue'
import SchemaTree from '@/components/schema/SchemaTree.vue'
import LogViewerDialog from '@/components/log/LogViewerDialog.vue'
import ToastHost from '@/components/ToastHost.vue'
import { zhCN } from '@/locales/zh-CN'

const backend = useBackendStore()
const connections = useConnectionStore()
const queryStore = useQueryStore()
const logOpen = ref(false)

/** 日志入口分流：已连接 → 主区"日志"页签；未连接 → 弹窗兜底 */
function openLogs(): void {
  if (connections.connectedIds.length > 0) {
    queryStore.setMainTab('log')
  } else {
    logOpen.value = true
  }
}

onMounted(() => {
  void backend.check()
  // 菜单"打开后端日志"
  window.joyo?.onOpenLogs(openLogs)
})
</script>

<template>
  <div class="app-layout">
    <aside class="app-sidebar">
      <div class="app-logo">Joyo</div>
      <!-- spec-01：连接管理；spec-02：对象树 -->
      <ConnectionList />
      <SchemaTree />
      <!-- F13 精简版：后端日志查看 -->
      <button class="log-entry" data-testid="open-logs" @click="openLogs">
        {{ zhCN.log.entry }}
      </button>
    </aside>
    <main class="app-main">
      <router-view />
    </main>
    <ToastHost />
    <LogViewerDialog :open="logOpen" @close="logOpen = false" />
  </div>
</template>

<style scoped>
.log-entry {
  margin-top: auto;
  padding: 6px;
  border: 1px dashed var(--joyo-border);
  border-radius: 6px;
  background: none;
  cursor: pointer;
  font-size: 12px;
  color: var(--joyo-text-muted);
}

.log-entry:hover {
  color: var(--joyo-primary);
  border-color: var(--joyo-primary);
}
</style>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
}

.app-sidebar {
  width: 260px;
  flex-shrink: 0;
  border-right: 1px solid var(--joyo-border);
  background: var(--joyo-bg-sidebar);
  display: flex;
  flex-direction: column;
  padding: 12px;
}

.app-logo {
  font-size: 20px;
  font-weight: 700;
  color: var(--joyo-primary);
  padding: 8px 4px 16px;
}

.sidebar-placeholder {
  color: var(--joyo-text-muted);
  font-size: 13px;
  padding: 12px 4px;
  border: 1px dashed var(--joyo-border);
  border-radius: 6px;
}

.app-main {
  flex: 1;
  overflow: auto;
  padding: 20px;
}
</style>
