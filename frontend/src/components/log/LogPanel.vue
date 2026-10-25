<script setup lang="ts">
import LogView from '@/components/log/LogView.vue'

/**
 * 后端日志面板（spec-13）：下半区"日志"页签内容，核心逻辑见 LogView（页签常驻 active）。
 */
</script>

<template>
  <LogView embedded active class="panel-log" data-testid="log-panel" />
</template>

<style scoped>
.panel-log {
  height: 100%;
}
</style>
