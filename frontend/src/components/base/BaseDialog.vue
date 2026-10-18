<script setup lang="ts">
import { computed } from 'vue'

/**
 * 对话框基座（ADR-002）：内部改用 el-dialog，对外 API 保持不变。
 * slot 名保持默认插槽，调用方无需改动。
 */

const props = defineProps<{
  title: string
  open: boolean
  width?: number
}>()

const emit = defineEmits<{ close: [] }>()

const modelValue = computed({
  get: () => props.open,
  set: (value: boolean) => {
    if (!value) emit('close')
  },
})
</script>

<template>
  <el-dialog
    v-model="modelValue"
    :title="title"
    :width="`${width ?? 480}px`"
    :close-on-click-modal="true"
    append-to-body
    destroy-on-close
  >
    <slot />
  </el-dialog>
</template>
