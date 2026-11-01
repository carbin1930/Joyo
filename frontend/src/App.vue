<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useBackendStore } from '@/stores/backend'
import { useConnectionStore } from '@/stores/connection'
import { useQueryStore } from '@/stores/query'
import ConnectionList from '@/components/connection/ConnectionList.vue'
import SchemaTree from '@/components/schema/SchemaTree.vue'
import LogViewerDialog from '@/components/log/LogViewerDialog.vue'
import AboutDialog from '@/components/about/AboutDialog.vue'
import StatusBar from '@/components/status/StatusBar.vue'
import ToastHost from '@/components/ToastHost.vue'
import { useSettingsStore } from '@/stores/settings'
import { zhCN } from '@/locales/zh-CN'

const backend = useBackendStore()
const connections = useConnectionStore()
const queryStore = useQueryStore()
const settings = useSettingsStore()
const logOpen = ref(false)
/** 关于面板（菜单 → 帮助 → 关于 Joyo） */
const aboutOpen = ref(false)
let offAbout: (() => void) | undefined

/** 日志入口分流：已连接 → 主区"日志"页签；未连接 → 弹窗兜底 */
function openLogs(): void {
  if (connections.connectedIds.length > 0) {
    queryStore.setMainTab('log')
  } else {
    logOpen.value = true
  }
}

onMounted(async () => {
  void backend.check()
  // 菜单"打开后端日志"
  window.joyo?.onOpenLogs(openLogs)
  // 菜单"关于 Joyo"
  offAbout = window.joyo?.onOpenAbout(() => {
    aboutOpen.value = true
    // 打开时刷新一次后端信息，确保 Java 版本/版本号是最新的
    void backend.check()
  })
  // spec-18：启动即加载主题/字号/行数上限
  await settings.load()
  queryStore.maxRows = settings.settings.maxRows
})

onUnmounted(() => offAbout?.())
</script>

<template>
  <div class="app-layout">
    <div class="app-body">
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
    </div>
    <!-- spec-14：全局资源状态栏 -->
    <StatusBar />
    <ToastHost />
    <LogViewerDialog :open="logOpen" @close="logOpen = false" />
    <AboutDialog :open="aboutOpen" @close="aboutOpen = false" />
  </div>
</template>

<style scoped>
.log-entry {
  margin-top: auto;
  height: var(--joyo-h-control);
  padding: 0 10px;
  border: 1px solid var(--joyo-border);
  border-radius: var(--joyo-radius);
  background: var(--joyo-bg-card);
  cursor: pointer;
  font-size: var(--joyo-fs-sm);
  color: var(--joyo-text-muted);
  transition:
    color var(--joyo-transition),
    border-color var(--joyo-transition),
    background-color var(--joyo-transition);
}

.log-entry:hover {
  color: var(--joyo-primary);
  border-color: var(--joyo-primary);
  background: var(--joyo-primary-weak);
}
</style>

<style scoped>
.app-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

/* spec-14：主区改为行布局，底部留出状态栏 */
.app-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.app-sidebar {
  width: 256px;
  flex-shrink: 0;
  border-right: 1px solid var(--joyo-border);
  background: var(--joyo-bg-sidebar);
  display: flex;
  flex-direction: column;
  gap: var(--joyo-space-3);
  padding: var(--joyo-space-3);
}

.app-logo {
  display: flex;
  align-items: center;
  gap: var(--joyo-space-2);
  font-size: var(--joyo-fs-xl);
  font-weight: 700;
  letter-spacing: 0.4px;
  color: var(--joyo-text);
  padding: 4px 4px 10px;
}

/* 品牌点：给 logo 一个识别色，避免整片灰 */
.app-logo::before {
  content: '';
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--joyo-primary);
  box-shadow: 0 0 0 3px var(--joyo-primary-weak);
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
  min-width: 0;
  overflow: hidden;
  background: var(--joyo-bg);
}
</style>
