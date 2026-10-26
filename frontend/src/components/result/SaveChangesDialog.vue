<script setup lang="ts">
import { ref, watch } from 'vue'
import BaseDialog from '@/components/base/BaseDialog.vue'
import type { ChangeSummary } from '@/composables/use-result-edit'
import { zhCN } from '@/locales/zh-CN'

/**
 * 保存确认弹窗（spec-16 第 2 节）：展示变更摘要，可展开查看将执行的语句预览。
 * 真正的语句由后端参数化生成，这里仅作展示。
 */

const props = defineProps<{
  open: boolean
  summary: ChangeSummary
  statements: string[]
  saving: boolean
}>()

const emit = defineEmits<{ close: []; confirm: [] }>()

const expanded = ref(false)

watch(
  () => props.open,
  (open) => {
    if (open) expanded.value = false
  },
)
</script>

<template>
  <BaseDialog
    :open="props.open"
    :title="zhCN.edit.confirmTitle"
    :width="480"
    @close="emit('close')"
  >
    <div class="save-dialog" data-testid="save-changes-dialog">
      <div class="summary-row">
        <span v-if="props.summary.insert" class="tag tag-insert">
          {{ zhCN.edit.summaryInsert }} {{ props.summary.insert }}
        </span>
        <span v-if="props.summary.update" class="tag tag-update">
          {{ zhCN.edit.summaryUpdate }} {{ props.summary.update }}
        </span>
        <span v-if="props.summary.delete" class="tag tag-delete">
          {{ zhCN.edit.summaryDelete }} {{ props.summary.delete }}
        </span>
      </div>

      <button class="toggle" data-testid="toggle-statements" @click="expanded = !expanded">
        {{ zhCN.edit.viewStatements }}（{{ props.statements.length }}）
      </button>
      <div v-if="expanded" class="statements" data-testid="statement-list">
        <div v-for="(sql, i) in props.statements" :key="i" class="statement">{{ sql }};</div>
      </div>

      <div class="actions">
        <el-button size="small" @click="emit('close')">{{ zhCN.common.cancel }}</el-button>
        <el-button
          size="small"
          type="danger"
          data-testid="confirm-save"
          :loading="props.saving"
          @click="emit('confirm')"
        >
          {{ zhCN.edit.save }}
        </el-button>
      </div>
    </div>
  </BaseDialog>
</template>

<style scoped>
.save-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.summary-row {
  display: flex;
  gap: 8px;
}

.tag {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.tag-insert {
  background: #e6f4ea;
  color: #1a7f37;
}

.tag-update {
  background: #fdf3d8;
  color: #8a6100;
}

.tag-delete {
  background: #fbe9e9;
  color: #b62324;
}

.toggle {
  align-self: flex-start;
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
  font-size: 12px;
  color: var(--joyo-primary);
}

.statements {
  max-height: 220px;
  overflow: auto;
  border: 1px solid var(--joyo-border);
  border-radius: 4px;
  padding: 8px;
  background: #fafbfc;
}

.statement {
  font-family: Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  color: var(--joyo-text);
  word-break: break-all;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
