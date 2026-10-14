<script setup lang="ts">
import { onMounted } from 'vue'
import { useBackendStore } from '@/stores/backend'

const backend = useBackendStore()

onMounted(() => {
  void backend.check()
})
</script>

<template>
  <div class="app-layout">
    <aside class="app-sidebar">
      <div class="app-logo">Joyo</div>
      <!-- spec-01 连接管理将在此渲染连接列表 -->
      <div class="sidebar-placeholder">连接列表（spec-01 实现）</div>
    </aside>
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

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
