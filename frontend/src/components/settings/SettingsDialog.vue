<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import BaseDialog from '@/components/base/BaseDialog.vue'
import UpdateDialog from '@/components/update/UpdateDialog.vue'
import { useSettingsStore } from '@/stores/settings'
import { useQueryStore } from '@/stores/query'
import { zhCN } from '@/locales/zh-CN'

/**
 * 设置面板（spec-18/F10）：外观（主题、字号）与查询（默认行数、超时）。
 * 改动即时生效（写入 CSS 变量与查询 store），保存到 Main 持久化。
 */

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

const settings = useSettingsStore()
const query = useQueryStore()

const theme = ref<'light' | 'dark'>('light')
const fontSize = ref(13)
const maxRows = ref(1000)
const queryTimeoutSec = ref(30)
const saving = ref(false)
/** spec-19：更新弹窗（从设置面板进入） */
const updateOpen = ref(false)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    theme.value = settings.settings.theme
    fontSize.value = settings.settings.fontSize
    maxRows.value = settings.settings.maxRows
    queryTimeoutSec.value = settings.settings.queryTimeoutSec
  },
)

const dirty = computed(
  () =>
    theme.value !== settings.settings.theme ||
    fontSize.value !== settings.settings.fontSize ||
    maxRows.value !== settings.settings.maxRows ||
    queryTimeoutSec.value !== settings.settings.queryTimeoutSec,
)

async function apply(): Promise<void> {
  if (saving.value) return
  saving.value = true
  try {
    // 字体与主题即时预览：直接写 DOM，保存失败也不影响当前会话观感
    document.documentElement.dataset.theme = theme.value
    document.documentElement.style.setProperty('--joyo-font-size', `${fontSize.value}px`)
    await settings.save({
      theme: theme.value,
      fontSize: fontSize.value,
      maxRows: maxRows.value,
      queryTimeoutSec: queryTimeoutSec.value,
    })
    // 行数上限是查询参数，同步给查询 store 立即生效
    query.maxRows = settings.settings.maxRows
  } finally {
    saving.value = false
    emit('close')
  }
}
</script>

<template>
  <BaseDialog :open="props.open" :title="zhCN.settings.title" :width="480" @close="emit('close')">
    <div class="settings" data-testid="settings-dialog">
      <div class="group">
        <div class="group-title">{{ zhCN.settings.appearance }}</div>
        <div class="row">
          <span class="label">{{ zhCN.settings.theme }}</span>
          <el-radio-group v-model="theme">
            <el-radio value="light" data-testid="theme-light">{{ zhCN.settings.themeLight }}</el-radio>
            <el-radio value="dark" data-testid="theme-dark">{{ zhCN.settings.themeDark }}</el-radio>
          </el-radio-group>
        </div>
        <div class="row">
          <span class="label">{{ zhCN.settings.fontSize }}</span>
          <el-slider v-model="fontSize" :min="12" :max="18" show-input style="flex: 1" />
        </div>
      </div>

      <div class="group">
        <div class="group-title">{{ zhCN.settings.query }}</div>
        <div class="row">
          <span class="label">{{ zhCN.settings.maxRows }}</span>
          <el-select v-model="maxRows" style="width: 160px">
            <el-option label="100" :value="100" />
            <el-option label="1000" :value="1000" />
            <el-option label="10000" :value="10000" />
          </el-select>
        </div>
        <div class="row">
          <span class="label">{{ zhCN.settings.queryTimeout }}</span>
          <el-input-number v-model="queryTimeoutSec" :min="5" :max="600" :controls="false" />
        </div>
      </div>

      <!-- spec-19：更新入口 -->
      <div class="row">
        <span class="label">{{ zhCN.updater.title }}</span>
        <el-button size="small" data-testid="check-update" @click="updateOpen = true">
          {{ zhCN.updater.check }}
        </el-button>
      </div>

      <div class="actions">
        <el-button size="small" @click="emit('close')">{{ zhCN.common.cancel }}</el-button>
        <el-button
          size="small"
          type="primary"
          data-testid="settings-save"
          :disabled="!dirty"
          :loading="saving"
          @click="apply"
        >
          {{ zhCN.settings.save }}
        </el-button>
      </div>

      <UpdateDialog :open="updateOpen" @close="updateOpen = false" />
    </div>
  </BaseDialog>
</template>

<style scoped>
.settings {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.group-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--joyo-text-muted);
}

.row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.label {
  width: 96px;
  font-size: 13px;
  color: var(--joyo-text-muted);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 4px;
}
</style>
