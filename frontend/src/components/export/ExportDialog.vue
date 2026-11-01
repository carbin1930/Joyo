<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import BaseDialog from '@/components/base/BaseDialog.vue'
import { recordHistory } from '@/composables/use-history'
import { useConnectionStore } from '@/stores/connection'
import { toast } from '@/composables/use-toast'
import { zhCN } from '@/locales/zh-CN'

/**
 * 导出弹窗（spec-15 第 2 节）：查询结果导出与整表导出共用。
 * 实际导出经 Main IPC（保存对话框 + 拉流写盘），渲染层不经手文件内容。
 */

interface ExportSource {
  connectionId: string
  database?: string
  sql: string
  /** INSERT 默认表名（整表导出时传表名） */
  defaultTable?: string
}

const props = defineProps<{ open: boolean; source: ExportSource | null }>()
const emit = defineEmits<{ close: [] }>()

const format = ref<'csv' | 'insert' | 'xlsx'>('csv')
const tableName = ref('')
const rowLimit = ref<number | null>(null)
const exporting = ref(false)

watch(
  () => props.open,
  (open) => {
    if (open) {
      format.value = 'csv'
      tableName.value = props.source?.defaultTable ?? ''
      rowLimit.value = null
      exporting.value = false
    }
  },
)

const ext = computed(() => (format.value === 'csv' ? 'csv' : format.value === 'insert' ? 'sql' : 'xlsx'))

function formatBytes(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} B`
}

function timestamp(): string {
  const d = new Date()
  const p = (n: number): string => String(n).padStart(2, '0')
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`
}

async function doExport(): Promise<void> {
  if (!props.source || exporting.value) return
  if (format.value === 'insert' && !tableName.value.trim()) {
    toast('error', zhCN.export.tableNameRequired)
    return
  }
  const started = Date.now()
  // 先取出快照：TS 的类型收窄不会进入下面的回调
  const source = props.source
  const connectionName =
    useConnectionStore().items.find((c) => c.id === source.connectionId)?.name ?? ''
  exporting.value = true
  try {
    const base = format.value === 'insert' ? tableName.value.trim() : 'query-result'
    const result = await window.joyo?.exportRun({
      connectionId: props.source.connectionId,
      sql: props.source.sql,
      format: format.value,
      database: props.source.database || undefined,
      tableName: format.value === 'insert' ? tableName.value.trim() : undefined,
      rowLimit: rowLimit.value ?? undefined,
      defaultFileName: `${base}-${timestamp()}.${ext.value}`,
    })
    // 用户取消保存对话框不算执行 SQL，不记历史
    if (!result || result.canceled) return
    toast('success', `${zhCN.export.done}（${formatBytes(result.bytes)}）`)
    // spec-05：导出会在服务端重跑一次 SQL，必须留痕
    void recordHistory({
      sql: props.source.sql,
      connectionName,
      database: props.source.database ?? '',
      kind: 'EXPORT',
      elapsedMs: Date.now() - started,
      rowCount: null,
      success: true,
      errorMessage: null,
    })
    emit('close')
  } catch (e) {
    // Electron invoke 的错误带通道前缀，清理后展示
    const message = e instanceof Error ? e.message : String(e)
    const clean = message.replace(/^Error invoking remote method 'export:run':\s*(Error:\s*)?/, '')
    void recordHistory({
      sql: props.source.sql,
      connectionName,
      database: props.source.database ?? '',
      kind: 'EXPORT',
      elapsedMs: Date.now() - started,
      rowCount: null,
      success: false,
      errorMessage: clean,
    })
    toast('error', clean)
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <BaseDialog :open="props.open" :title="zhCN.export.title" :width="420" @close="emit('close')">
    <div class="export-form" data-testid="export-dialog">
      <div class="form-row">
        <span class="form-label">{{ zhCN.export.formatLabel }}</span>
        <el-radio-group v-model="format">
          <el-radio value="csv">{{ zhCN.export.formatCsv }}</el-radio>
          <el-radio value="insert">{{ zhCN.export.formatInsert }}</el-radio>
          <el-radio value="xlsx">{{ zhCN.export.formatXlsx }}</el-radio>
        </el-radio-group>
      </div>
      <div v-if="format === 'insert'" class="form-row">
        <span class="form-label">{{ zhCN.export.tableNameLabel }}</span>
        <el-input
          v-model="tableName"
          data-testid="export-table-name"
          :placeholder="zhCN.export.tableNameHint"
          style="flex: 1"
        />
      </div>
      <div class="form-row">
        <span class="form-label">{{ zhCN.export.rowLimitLabel }}</span>
        <el-input-number
          v-model="rowLimit"
          data-testid="export-row-limit"
          :min="1"
          :max="5000000"
          :controls="false"
          style="width: 160px"
        />
      </div>
      <div class="form-actions">
        <el-button @click="emit('close')">{{ zhCN.common.cancel }}</el-button>
        <el-button
          type="primary"
          data-testid="export-confirm"
          :loading="exporting"
          @click="doExport"
        >
          {{ exporting ? zhCN.export.exporting : zhCN.export.do }}
        </el-button>
      </div>
    </div>
  </BaseDialog>
</template>

<style scoped>
.export-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.form-label {
  width: 88px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--joyo-text-muted);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 6px;
}
</style>