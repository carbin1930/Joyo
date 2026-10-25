<script setup lang="ts">
import BaseDialog from '@/components/base/BaseDialog.vue'
import LogView from '@/components/log/LogView.vue'
import { zhCN } from '@/locales/zh-CN'

/**
 * 日志查看弹窗（spec-13 第 2 节，900 宽）：核心逻辑见 LogView；关闭即停止轮询（active=false）。
 */
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <BaseDialog :open="props.open" :title="zhCN.log.title" :width="900" @close="emit('close')">
    <LogView :active="props.open" class="dialog-log" />
  </BaseDialog>
</template>

<style scoped>
.dialog-log {
  height: 560px;
}
</style>
