<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { TabulatorFull as Tabulator } from 'tabulator-tables'
import type { CellComponent, ColumnDefinition } from 'tabulator-tables'
import type { ColumnMeta } from '@/types/api'
import { copyText, isBinaryType, toCsv, toTsv } from '@/composables/use-clipboard'
import { MAX_RENDERED_COLUMNS, toGridRows, visibleColumns } from '@/composables/use-result-grid'
import { toast } from '@/composables/use-toast'
import { zhCN } from '@/locales/zh-CN'

/**
 * 结果网格（spec-04）：Tabulator 只读 + 虚拟滚动 + 列宽拖拽 + 选区复制。
 * 渲染、选区、复制/编辑态都在此组件内；右键菜单由 ResultPanel 渲染（emit context）。
 */

const emit = defineEmits<{
  context: [payload: { x: number; y: number; hasSelection: boolean }]
}>()

/** 复制格式：TSV 供 Excel 粘贴，CSV 供文件/表格导入 */
type CopyFormat = 'tsv' | 'csv'
/** 复制范围：选中内容 / 当前页全部行 */
type CopyScope = 'selection' | 'all'

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
      // 双击进入编辑态仅用于选取文本复制（提交后由 cellEdited 还原原值）
      editor: 'input',
      selectContents: true,
    }),
  )
  return [rownum, ...dataColumns]
}

function buildRows() {
  return toGridRows(props.rows, renderedColumnCount(), props.page, props.pageSize)
}

// ===== 复制（spec-04 第 4 节）=====

function dataColumns(): ColumnMeta[] {
  return visibleColumns(props.columns, props.maxColumns)
}

/** 字段名 c{n} → 列类型；越界返回 undefined */
function typeOfField(field: string): string | undefined {
  if (!field.startsWith('c')) return undefined
  return dataColumns()[Number(field.slice(1))]?.type
}

/** 当前选区矩阵：优先单元格区域，其次整行选中；无选中返回 null */
function selectedMatrix(): { rows: (string | null)[][]; titles: string[]; types: string[] } | null {
  if (!table) return null
  const ranges = table.getRanges()
  if (ranges.length > 0) {
    const cells = ranges[0].getStructuredCells()
    if (cells.length === 0 || cells[0].length === 0) return null
    const head = cells[0]
    return {
      rows: cells.map((row) => row.map((cell) => cell.getValue() as string | null)),
      titles: head.map((cell) => String(cell.getColumn().getDefinition().title ?? '')),
      types: head.map((cell) => typeOfField(cell.getColumn().getField()) ?? ''),
    }
  }
  const selectedRows = table.getSelectedRows()
  if (selectedRows.length > 0) {
    const fields = dataColumns().map((_, ci) => `c${ci}`)
    return {
      rows: selectedRows.map((row) =>
        fields.map((field) => (row.getData()[field] ?? null) as string | null),
      ),
      titles: dataColumns().map((col) => col.name),
      types: dataColumns().map((col) => col.type),
    }
  }
  return null
}

function hasSelection(): boolean {
  if (!table) return false
  return table.getRanges().length > 0 || table.getSelectedRows().length > 0
}

/**
 * 复制到剪贴板：CSV 带表头（Excel 可直接识别列名），TSV 不带表头（粘贴到表格区域）。
 * scope=all 时复制当前页整页（50 列上限内）。
 */
async function copy(format: CopyFormat, scope: CopyScope = 'selection'): Promise<boolean> {
  let matrix: { rows: (string | null)[][]; titles: string[]; types: string[] } | null
  if (scope === 'all') {
    const count = renderedColumnCount()
    matrix = {
      rows: props.rows.map((row) =>
        Array.from({ length: count }, (_, ci) => (row[ci] === undefined ? null : row[ci])),
      ),
      titles: dataColumns().map((col) => col.name),
      types: dataColumns().map((col) => col.type),
    }
  } else {
    matrix = selectedMatrix()
  }

  if (!matrix || matrix.rows.length === 0) {
    toast('info', zhCN.query.copyEmpty)
    return false
  }

  const text =
    format === 'csv'
      ? toCsv(matrix.rows, { header: matrix.titles, types: matrix.types })
      : toTsv(matrix.rows, matrix.types)
  const ok = await copyText(text)
  toast(ok ? 'success' : 'error', ok ? zhCN.query.copied(matrix.rows.length) : zhCN.query.copyFailed)
  return ok
}

/** Ctrl+C：把选中区域复制为 TSV（Excel 可直接粘贴） */
function onKeydown(event: KeyboardEvent): void {
  if (!(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== 'c') return
  if (!hasSelection()) return
  event.preventDefault()
  void copy('tsv', 'selection')
}

/** 屏蔽浏览器原生右键菜单，改用面板内的复制菜单 */
function onContextMenu(event: MouseEvent): void {
  event.preventDefault()
}

defineExpose({ copy })

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
    // 单元格区域选择（Ctrl+C 复制）与整行选择
    selectableRange: true,
    selectableRows: true,
    clipboard: false,
  })

  // 双击进入编辑态仅为复制文本（回车/Esc 退出后还原原值，不写库）
  table.on('cellDblClick', (_event: Event, cell: CellComponent) => {
    void cell.edit(true)
  })
  table.on('cellEdited', (cell: CellComponent) => {
    const oldValue = cell.getOldValue()
    if (oldValue !== undefined && oldValue !== null) cell.setValue(oldValue, false)
  })
  table.on('cellContext', (event: Event, _cell: CellComponent) => {
    const mouse = event as MouseEvent
    emit('context', { x: mouse.clientX, y: mouse.clientY, hasSelection: hasSelection() })
  })

  hostRef.value.addEventListener('keydown', onKeydown)
  hostRef.value.addEventListener('contextmenu', onContextMenu)
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
  hostRef.value?.removeEventListener('keydown', onKeydown)
  hostRef.value?.removeEventListener('contextmenu', onContextMenu)
  table?.destroy()
  table = null
})
</script>

<template>
  <div ref="hostRef" class="joyo-result-grid" data-testid="result-grid" />
</template>
