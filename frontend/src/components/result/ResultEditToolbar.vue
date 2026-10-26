<script setup lang="ts">
import { computed } from 'vue'
import type { EditableInfo } from '@/types/api'
import type { ChangeSummary } from '@/composables/use-result-edit'
import { zhCN } from '@/locales/zh-CN'

/**
 * 结果编辑工具栏（spec-16 第 2 节）：
 * 锁定态只显示状态与"解锁编辑"；解锁态提供新增行 / 删除行 / 保存 / 放弃。
 * 不可编辑的结果只显示一行灰字原因。
 */

const props = defineProps<{
  editable: EditableInfo | null
  unlocked: boolean
  summary: ChangeSummary
  dirty: boolean
  saving: boolean
  hasSelection: boolean
}>()

const emit = defineEmits<{
  toggle: []
  addRow: []
  deleteRow: []
  save: []
  discard: []
}>()

const changeText = computed(() =>
  zhCN.edit.changes(props.summary.insert, props.summary.update, props.summary.delete),
)
</script>

<template>
  <div class="edit-toolbar" data-testid="edit-toolbar">
    <template v-if="!props.editable">
      <span class="edit-hint" data-testid="edit-not-editable">{{ zhCN.edit.notEditable }}</span>
    </template>

    <template v-else-if="!props.unlocked">
      <span class="edit-status locked">
        <span class="lock-icon">🔒</span>{{ zhCN.edit.locked }}
      </span>
      <span class="spacer" />
      <el-button size="small" data-testid="edit-unlock" @click="emit('toggle')">
        {{ zhCN.edit.unlock }}
      </el-button>
    </template>

    <template v-else>
      <span class="edit-status editing">
        <span class="lock-icon">🔓</span>{{ zhCN.edit.editing }}
      </span>
      <span v-if="props.dirty" class="change-badge" data-testid="edit-changes">
        {{ changeText }}
      </span>
      <span class="spacer" />
      <el-button size="small" data-testid="edit-add-row" @click="emit('addRow')">
        {{ zhCN.edit.addRow }}
      </el-button>
      <el-button
        size="small"
        data-testid="edit-delete-row"
        :disabled="!props.hasSelection"
        @click="emit('deleteRow')"
      >
        {{ zhCN.edit.deleteRow }}
      </el-button>
      <el-button size="small" :disabled="!props.dirty" @click="emit('discard')">
        {{ zhCN.edit.discard }}
      </el-button>
      <el-button
        size="small"
        type="primary"
        data-testid="edit-save"
        :disabled="!props.dirty"
        :loading="props.saving"
        @click="emit('save')"
      >
        {{ props.saving ? zhCN.edit.saving : zhCN.edit.save }}
      </el-button>
    </template>
  </div>
</template>

<style scoped>
.edit-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  flex-shrink: 0;
  padding: 0 8px;
  border-bottom: 1px solid var(--joyo-border);
  background: var(--joyo-bg-subtle, #f7f8fa);
  font-size: 12px;
}

.edit-status {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--joyo-text-muted);
}

.edit-status.editing {
  color: var(--joyo-primary);
  font-weight: 600;
}

.lock-icon {
  font-size: 11px;
  transition: transform 150ms ease;
}

.change-badge {
  padding: 1px 6px;
  border-radius: 8px;
  background: #fdf3d8;
  color: #8a6100;
  font-size: 11px;
}

.edit-hint {
  color: var(--joyo-text-muted);
}

.spacer {
  flex: 1;
}
</style>
