<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { TabulatorFull as Tabulator } from 'tabulator-tables'
import type { CellComponent, ColumnDefinition } from 'tabulator-tables'
import type { ColumnMeta } from '@/types/api'
import { isBinaryType } from '@/composables/use-clipboard'
import { MAX_RENDERED_COLUMNS, toGridRows, visibleColumns } from '@/composables/use-result-grid'
import { zhCN } from '@/locales/zh-CN'

/**
 * 结果网格（spec-04）：Tabulator 只读 + 虚拟滚动 + 列宽拖拽。
 * 只负责渲染，复制/编辑等交互由 ResultPanel 组合。
 */

const props = withDefaults(
  defineProps<{
    columns: ColumnMeta[]
    rows: (string | null)[][]
    /** 当前页（1 起），用于行号连续 */
    page?: number
    pageSize?: number
    maxColumns?: number
  }>(),
  { page: 1, pageSize: 100, maxColumns: MAX_RENDERED_COLUMNS },
)

/** tooltip 全文上限，超长值直接截断（spec-04 第 2 节） */
const TOOLTIP_MAX = 2000

const hostRef = ref<HTMLDivElement | null>(null)
let table: Tabulator | null = null

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** 单元格渲染：NULL 灰色斜体；BLOB 只显示占位；其余纯文本（转义防注入） */
function renderCell(cell: CellComponent, type: string): string {
  const value = cell.getValue() as string | null | undefined
  const isNull = value === null || value === undefined
  cell.getElement().classList.toggle('joyo-cell-null-wrap', isNull)
  if (isNull) return '<span class="joyo-cell-null">NULL</span>'
  if (isBinaryType(type)) return '<span class="joyo-cell-blob">(BLOB)</span>'
  return escapeHtml(String(value))
}

/** 悬停显示全文，截断到 TOOLTIP_MAX 字符 */
function cellTooltip(cell: CellComponent, type: string): string {
  const value = cell.getValue() as string | null | undefined
  if (value === null || value === undefined) return 'NULL'
  if (isBinaryType(type)) return '(BLOB)'
  const text = String(value)
  return text.length > TOOLTIP_MAX ? text.slice(0, TOOLTIP_MAX) : text
}

function renderedColumnCount(): number {
  return visibleColumns(props.columns, props.maxColumns).length
}

function buildColumns(): ColumnDefinition[] {
  const rownum: ColumnDefinition = {
    title: '#',
    field: '__rownum',
    width: 56,
    frozen: true,
    hozAlign: 'right',
    headerSort: false,
    cssClass: 'joyo-rownum',
  }
  const dataColumns = visibleColumns(props.columns, props.maxColumns).map<ColumnDefinition>(
    (col, ci) => ({
      title: col.name,
      field: `c${ci}`,
      headerTooltip: col.type,
      minWidth: 60,
      formatter: (cell: CellComponent) => renderCell(cell, col.type),
      tooltip: (_event: Event, cell: CellComponent) => cellTooltip(cell, col.type),
    }),
  )
  return [rownum, ...dataColumns]
}

function buildRows() {
  return toGridRows(props.rows, renderedColumnCount(), props.page, props.pageSize)
}

onMounted(() => {
  if (!hostRef.value) return
  table = new Tabulator(hostRef.value, {
    data: buildRows(),
    columns: buildColumns(),
    layout: 'fitData',
    height: '100%',
    placeholder: zhCN.query.zeroRows,
    reactiveData: false,
    columnHeaderVertAlign: 'middle',
    // 纵向虚拟滚动由 Tabulator 默认提供；行数上万时只渲染可视区
    renderVertical: 'virtual',
  })
})

watch(
  () => [props.columns, props.rows, props.page, props.pageSize, props.maxColumns],
  () => {
    if (!table) return
    table.setColumns(buildColumns())
    void table.replaceData(buildRows())
  },
)

onBeforeUnmount(() => {
  table?.destroy()
  table = null
})
</script>

<template>
  <div ref="hostRef" class="joyo-result-grid" data-testid="result-grid" />
</template>
