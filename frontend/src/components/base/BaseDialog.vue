<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'

defineProps<{
  title: string
  open: boolean
  width?: number
}>()

const emit = defineEmits<{ close: [] }>()

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape') emit('close')
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="dialog-overlay" @mousedown.self="emit('close')">
      <div class="dialog-panel" :style="{ width: `${width ?? 480}px` }" role="dialog" :aria-label="title">
        <div class="dialog-header">
          <span class="dialog-title">{{ title }}</span>
          <button class="dialog-close" aria-label="关闭" @click="emit('close')">×</button>
        </div>
        <div class="dialog-body">
          <slot />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgb(0 0 0 / 40%);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 500;
}

.dialog-panel {
  background: var(--joyo-bg-card);
  border-radius: 10px;
  box-shadow: 0 12px 40px rgb(0 0 0 / 20%);
  max-height: 85vh;
  display: flex;
  flex-direction: column;
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--joyo-border);
}

.dialog-title {
  font-weight: 600;
}

.dialog-close {
  border: none;
  background: none;
  font-size: 18px;
  cursor: pointer;
  color: var(--joyo-text-muted);
  line-height: 1;
}

.dialog-close:hover {
  color: var(--joyo-text);
}

.dialog-body {
  padding: 18px;
  overflow: auto;
}
</style>
