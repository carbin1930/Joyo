<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import BaseDialog from '@/components/base/BaseDialog.vue'
import { compareTables, executeSync } from '@/api/sync'
import { getSchemas, getTables } from '@/api/metadata'
import { useConnectionStore } from '@/stores/connection'
import { toast } from '@/composables/use-toast'
import type { ColumnDiff, SyncCompareResponse, SyncTableRef } from '@/types/api'
import { zhCN } from '@/locales/zh-CN'

/**
 * 同步面板（spec-17 第 2 节）：源/目标选择 → 结构与数据差异展示 → 脚本预览 → 确认执行。
 * 源默认带出当前表，目标默认同源；比对与执行都走后端，渲染层不做任何差异计算。
 */

const props = defineProps<{ open: boolean; source: SyncTableRef | null }>()
const emit = defineEmits<{ close: [] }>()

const connections = useConnectionStore()

const targetConnectionId = ref('')
const schemas = ref<string[]>([])
const targetSchema = ref('')
const tables = ref<string[]>([])
const targetTable = ref('')
const rowLimit = ref(10000)
const result = ref<SyncCompareResponse | null>(null)
const comparing = ref(false)
const executing = ref(false)
const expanded = ref(false)

/** 已建立会话的连接（只有会话存在后端才能取连接） */
const connectionOptions = computed(() =>
  connections.connectedIds.map((id) => ({
    id,
    name: connections.items.find((c) => c.id === id)?.name ?? id,
  })),
)

const hasDiff = computed(() => {
  const data = result.value
  if (!data?.comparable) return false
  return (
    data.columnDiffs.length > 0 ||
    data.indexDiffs.length > 0 ||
    data.dataDiff.insert + data.dataDiff.update + data.dataDiff.delete > 0
  )
})

watch(
  () => props.open,
  (open) => {
    if (!open) return
    result.value = null
    expanded.value = false
    rowLimit.value = 10000
    const source = props.source
    if (!source) return
    // 目标默认同源（用户改成别的库/连接即可跨库或跨实例比对）
    targetConnectionId.value = source.connectionId
    targetSchema.value = source.database
    targetTable.value = source.table
    void loadSchemas(source.connectionId)
  },
)

async function loadSchemas(connectionId: string): Promise<void> {
  try {
    const resp = await getSchemas(connectionId)
    schemas.value = resp.schemas.map((s) => s.name)
  } catch (e) {
    schemas.value = []
    toast('error', e instanceof Error ? e.message : String(e))
  }
}

async function loadTables(): Promise<void> {
  if (!targetConnectionId.value || !targetSchema.value) return
  try {
    const resp = await getTables(targetConnectionId.value, targetSchema.value)
    tables.value = resp.tables.map((t) => t.name)
    if (!tables.value.includes(targetTable.value)) {
      targetTable.value = props.source?.table ?? ''
    }
  } catch (e) {
    tables.value = []
    toast('error', e instanceof Error ? e.message : String(e))
  }
}

function onConnectionChange(): void {
  schemas.value = []
  tables.value = []
  targetSchema.value = ''
  void loadSchemas(targetConnectionId.value)
}

function onSchemaChange(): void {
  void loadTables()
}

async function compare(): Promise<void> {
  const source = props.source
  if (!source) return
  if (!targetConnectionId.value || !targetSchema.value || !targetTable.value) {
    toast('error', zhCN.sync.noTarget)
    return
  }
  comparing.value = true
  try {
    result.value = await compareTables({
      source,
      target: {
        connectionId: targetConnectionId.value,
        database: targetSchema.value,
        table: targetTable.value,
      },
      rowLimit: rowLimit.value,
    })
  } catch (e) {
    result.value = null
    toast('error', e instanceof Error ? e.message : String(e))
  } finally {
    comparing.value = false
  }
}

async function execute(): Promise<void> {
  const source = props.source
  if (!source) return
  executing.value = true
  try {
    const resp = await executeSync({
      source,
      target: {
        connectionId: targetConnectionId.value,
        database: targetSchema.value,
        table: targetTable.value,
      },
      rowLimit: rowLimit.value,
    })
    toast('success', zhCN.sync.done(resp.applied))
    emit('close')
  } catch (e) {
    toast('error', e instanceof Error ? e.message : String(e))
  } finally {
    executing.value = false
  }
}

function kindText(kind: string): string {
  return kind === 'missing'
    ? zhCN.sync.kindMissing
    : kind === 'extra'
      ? zhCN.sync.kindExtra
      : zhCN.sync.kindChanged
}

function kindClass(col: ColumnDiff): string {
  return `diff-${col.kind}`
}
</script>

<template>
  <BaseDialog :open="props.open" :title="zhCN.sync.title" :width="720" @close="emit('close')">
    <div class="sync-panel" data-testid="sync-dialog">
      <div class="pick">
        <span class="pick-label">{{ zhCN.sync.source }}</span>
        <span class="pick-static" data-testid="sync-source">
          {{ props.source ? `${props.source.database}.${props.source.table}` : '-' }}
        </span>
      </div>
      <div class="pick">
        <span class="pick-label">{{ zhCN.sync.target }}</span>
        <el-select
          v-model="targetConnectionId"
          data-testid="sync-target-conn"
          style="width: 150px"
          @change="onConnectionChange"
        >
          <el-option v-for="c in connectionOptions" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select
          v-model="targetSchema"
          data-testid="sync-target-schema"
          style="width: 140px"
          @change="onSchemaChange"
        >
          <el-option v-for="s in schemas" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="targetTable" data-testid="sync-target-table" style="width: 160px" filterable>
          <el-option v-for="t in tables" :key="t" :label="t" :value="t" />
        </el-select>
        <el-button size="small" data-testid="sync-compare" :loading="comparing" @click="compare">
          {{ comparing ? zhCN.sync.comparing : zhCN.sync.compare }}
        </el-button>
      </div>

      <template v-if="result && !result.comparable">
        <div class="notice" data-testid="sync-not-comparable">
          {{ zhCN.sync.notComparable }}：{{ result.reason }}
        </div>
      </template>

      <template v-else-if="result">
        <div class="diff-area">
          <div class="diff-block">
            <div class="block-title">{{ zhCN.sync.columns }}</div>
            <div v-if="result.columnDiffs.length === 0" class="empty">{{ zhCN.sync.noDiff }}</div>
            <table v-else class="diff-table">
              <thead>
                <tr>
                  <th>{{ zhCN.sync.columnName }}</th>
                  <th>{{ zhCN.sync.sourceType }}</th>
                  <th>{{ zhCN.sync.targetType }}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                <tr v-for="col in result.columnDiffs" :key="col.name" :class="kindClass(col)">
                  <td>{{ col.name }}</td>
                  <td>{{ col.sourceType ?? '-' }}</td>
                  <td>{{ col.targetType ?? '-' }}</td>
                  <td class="kind">{{ kindText(col.kind) }}</td>
                </tr>
              </tbody>
            </table>

            <div v-if="result.indexDiffs.length > 0" class="block-title sub">
              {{ zhCN.sync.indexes }}
            </div>
            <div v-for="idx in result.indexDiffs" :key="idx.name" class="index-row">
              <span class="idx-name">{{ idx.name }}</span>
              <span class="idx-detail">{{ idx.detail ?? '' }}</span>
              <span class="kind">{{ kindText(idx.kind) }}</span>
            </div>
          </div>

          <div class="diff-block narrow">
            <div class="block-title">{{ zhCN.sync.data }}</div>
            <div class="counters">
              <span class="counter c-insert">{{ zhCN.sync.insert }} {{ result.dataDiff.insert }}</span>
              <span class="counter c-update">{{ zhCN.sync.update }} {{ result.dataDiff.update }}</span>
              <span class="counter c-delete">{{ zhCN.sync.delete }} {{ result.dataDiff.delete }}</span>
            </div>
            <div class="scanned">{{ zhCN.sync.scanned(result.dataDiff.scanned) }}</div>
            <div v-if="result.dataDiff.truncated" class="warn">
              {{ zhCN.sync.truncated(rowLimit) }}
            </div>
          </div>
        </div>

        <button class="toggle" @click="expanded = !expanded">
          {{ zhCN.sync.statements }}（{{ result.statements.length }}）
        </button>
        <div v-if="expanded" class="statements" data-testid="sync-statements">
          <div v-for="(sql, i) in result.statements" :key="i" class="statement">{{ sql }};</div>
        </div>
        <div class="ddl-warn">{{ zhCN.sync.ddLWarning }}</div>
      </template>

      <div v-else class="empty hint">选择目标后点击"{{ zhCN.sync.compare }}"</div>

      <div class="actions">
        <el-button size="small" @click="emit('close')">{{ zhCN.common.cancel }}</el-button>
        <el-button
          size="small"
          type="danger"
          data-testid="sync-execute"
          :disabled="!hasDiff"
          :loading="executing"
          @click="execute"
        >
          {{ executing ? zhCN.sync.executing : zhCN.sync.do }}
        </el-button>
      </div>
    </div>
  </BaseDialog>
</template>

<style scoped>
.sync-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pick {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pick-label {
  width: 48px;
  font-size: 13px;
  color: var(--joyo-text-muted);
}

.pick-static {
  font-family: Consolas, monospace;
  font-size: 13px;
}

.diff-area {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.diff-block {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--joyo-border);
  border-radius: 4px;
  padding: 8px;
}

.diff-block.narrow {
  flex: 0 0 180px;
}

.block-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--joyo-text-muted);
  margin-bottom: 6px;
}

.block-title.sub {
  margin-top: 10px;
}

.diff-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.diff-table th {
  text-align: left;
  color: var(--joyo-text-muted);
  font-weight: 500;
  padding: 2px 4px;
}

.diff-table td {
  padding: 2px 4px;
  font-family: Consolas, monospace;
}

.diff-missing {
  background: #e6f4ea;
}

.diff-extra {
  background: #fbe9e9;
}

.diff-changed {
  background: #fdf3d8;
}

.kind {
  font-size: 11px;
  color: var(--joyo-text-muted);
}

.index-row {
  display: flex;
  gap: 6px;
  font-size: 12px;
  padding: 1px 0;
}

.idx-name {
  font-family: Consolas, monospace;
}

.idx-detail {
  color: var(--joyo-text-muted);
  flex: 1;
}

.counters {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.counter {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.c-insert {
  background: #e6f4ea;
  color: #1a7f37;
}

.c-update {
  background: #fdf3d8;
  color: #8a6100;
}

.c-delete {
  background: #fbe9e9;
  color: #b62324;
}

.scanned,
.warn {
  margin-top: 6px;
  font-size: 11px;
  color: var(--joyo-text-muted);
}

.warn {
  color: var(--joyo-danger);
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
  max-height: 140px;
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
  word-break: break-all;
}

.ddl-warn {
  font-size: 11px;
  color: var(--joyo-text-muted);
}

.notice {
  padding: 8px 10px;
  border: 1px solid var(--joyo-danger);
  border-radius: 4px;
  color: var(--joyo-danger);
  font-size: 12px;
}

.empty {
  font-size: 12px;
  color: var(--joyo-text-muted);
}

.empty.hint {
  padding: 12px 0;
  text-align: center;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 4px;
}
</style>
