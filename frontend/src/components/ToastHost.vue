<script setup lang="ts">
import { dismiss, useToastState } from '@/composables/use-toast'

const state = useToastState()
</script>

<template>
  <div class="toast-host">
    <div
      v-for="item in state.items"
      :key="item.id"
      class="toast"
      :class="`toast-${item.type}`"
      role="status"
      @click="dismiss(item.id)"
    >
      {{ item.message }}
    </div>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  right: 16px;
  bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  z-index: 1000;
}

.toast {
  padding: 10px 14px;
  border-radius: 8px;
  background: var(--joyo-bg-card);
  border: 1px solid var(--joyo-border);
  box-shadow: 0 4px 12px rgb(0 0 0 / 12%);
  font-size: 13px;
  max-width: 360px;
  cursor: pointer;
}

.toast-success {
  border-color: var(--joyo-success);
}

.toast-error {
  border-color: var(--joyo-danger);
  color: var(--joyo-danger);
}
</style>
