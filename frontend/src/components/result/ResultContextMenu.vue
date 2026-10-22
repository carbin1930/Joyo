<script setup lang="ts">
import { zhCN } from '@/locales/zh-CN'

/**
 * 结果网格右键菜单（spec-04 第 4 节）：复制为 CSV / TSV。
 * 关闭时机（点击空白、Esc、切换页签）由父组件 ResultPanel 统一处理。
 */

defineProps<{
  /** 视口坐标（position: fixed） */
  x: number
  y: number
}>()

const emit = defineEmits<{
  copy: [format: 'csv' | 'tsv']
}>()
</script>

<template>
  <div
    class="ctx-menu"
    data-testid="result-context-menu"
    :style="{ left: `${x}px`, top: `${y}px` }"
    @click.stop
  >
    <button class="ctx-item" @click="emit('copy', 'csv')">{{ zhCN.query.copyCsv }}</button>
    <button class="ctx-item" @click="emit('copy', 'tsv')">{{ zhCN.query.copyTsv }}</button>
  </div>
</template>

<style scoped>
.ctx-menu {
  position: fixed;
  z-index: 3000;
  min-width: 132px;
  padding: 4px;
  display: flex;
  flex-direction: column;
  background: var(--joyo-bg-card);
  border: 1px solid var(--joyo-border);
  border-radius: 6px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
}

.ctx-item {
  border: none;
  background: none;
  text-align: left;
  padding: 6px 10px;
  font-size: 12px;
  color: var(--joyo-text);
  border-radius: 4px;
  cursor: pointer;
}

.ctx-item:hover {
  background: var(--joyo-bg-sidebar);
}
</style>
