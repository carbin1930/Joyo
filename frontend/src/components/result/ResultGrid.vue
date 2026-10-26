<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { TabulatorFull as Tabulator } from 'tabulator-tables'
import type { CellComponent, ColumnDefinition, RowComponent } from 'tabulator-tables'
import type { ColumnMeta, EditableInfo } from '@/types/api'
import { copyText, isBinaryType, toCsv, toTsv } from '@/composables/use-clipboard'
import {
  MAX_RENDERED_COLUMNS,
  toGridRows,
  visibleColumns,
  type GridRow,
} from '@/composables/use-result-grid'
import { parseCellInput, type RowChange } from '@/composables/use-result-edit'
import { toast } from '@/composables/use-toast'
import { zhCN } from '@/locales/zh-CN'

/**
 * 结果网格（spec-04 + spec-16）：Tabulator 虚拟滚动 + 列宽拖拽 + 选区复制。
 * 默认只读（双击编辑仅用于选取文本，提交后还原）；spec-16 解锁后单元格可提交改动（emit edit）。
 * 渲染、选区、复制/编辑态都在此组件内；右键菜单由 ResultPanel 渲染（emit context）。
 */

const emit = defineEmits<{
  context: [payload: { x: number; y: number; hasSelection: boolean }]
  /** spec-16：选区变化（工具栏"删除行"的可用态） */
  selection: [has: boolean]
  /** spec-16：解锁态下单元格提交改动（含该行主键原值快照） */
  edit: [payload: {
    rowIndex: number
    column: string
    value: string | null
    original: string | null
    pk: Record<string, string | null>
  }]
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
    /** spec-16：可编辑信息；null 表示不可编辑 */
    editable?: EditableInfo | null
    /** spec-16：是否解锁编辑 */
    unlocked?: boolean
    /** spec-16：本地改动（按全局行号索引，新增行为负数） */
    changes?: Record<number, RowChange>
  }>(),
  { page: 1, pageSize: 100, maxColumns: MAX_RENDERED_COLUMNS, editable: null, unlocked: false },
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

/** 主键列名集合（spec-16：主键列只读） */
function pkColumns(): Set<string> {
  return new Set(props.editable?.primaryKey ?? [])
}

/** 可编辑条件：已解锁 + 非主键列 + 非二进制列 */
function canEdit(col: ColumnMeta): boolean {
  return props.unlocked === true && !pkColumns().has(col.name) && !isBinaryType(col.type)
}

/** 字段名 c{n} → 列定义 */
function columnOfField(field: string): ColumnMeta | null {
  if (!field || !field.startsWith('c')) return null
  return visibleColumns(props.columns, props.maxColumns)[Number(field.slice(1))] ?? null
}

/** 该格是否有未保存改动（用于脏标记底纹） */
function isDirtyCell(rowIndex: number, column: string): boolean {
  const change = props.changes?.[rowIndex]
  return change?.kind === 'update' && change.values != null && column in change.values
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
  const locked = pkColumns()
  const dataColumns = visibleColumns(props.columns, props.maxColumns).map<ColumnDefinition>(
    (col, ci) => ({
      title: locked.has(col.name) ? `🔒 ${col.name}` : col.name,
      field: `c${ci}`,
      headerTooltip: locked.has(col.name) ? `${col.type} · ${zhCN.edit.pkReadonly}` : col.type,
      minWidth: 60,
      formatter: (cell: CellComponent) => {
        const html = renderCell(cell, col.type)
        const rowIndex = Number(cell.getRow().getData().__idx ?? Number.NaN)
        cell.getElement().classList.toggle('joyo-cell-dirty', isDirtyCell(rowIndex, col.name))
        return html
      },
      tooltip: (_event: Event, cell: CellComponent) => cellTooltip(cell, col.type),
      // 双击进入编辑态：锁定态仅用于选取文本复制（提交后由 cellEdited 还原原值）；解锁态可提交改动
      editor: 'input',
      selectContents: true,
      editable: () => canEdit(col),
    }),
  )
  return [rownum, ...dataColumns]
}

/**
 * 网格数据：基础行（全局行号 __idx）→ 叠加未保存的改动值 → 末尾追加新增行。
 * 新增行行号为负数，__rownum 显示"新"。
 */
function buildRows(): GridRow[] {
  const cols = visibleColumns(props.columns, props.maxColumns)
  const base = toGridRows(props.rows, cols.length, props.page, props.pageSize)
  const rows: GridRow[] = base.map((record) => {
    const rowIndex = Number(record.__rownum) - 1
    const merged: GridRow = { ...record, __idx: rowIndex }
    const change = props.changes?.[rowIndex]
    if (change?.kind === 'update' && change.values) {
      for (const [name, value] of Object.entries(change.values)) {
        const ci = cols.findIndex((c) => c.name === name)
        if (ci >= 0) merged[`c${ci}`] = value
      }
    }
    return merged
  })

  const inserts = Object.entries(props.changes ?? {})
    .map(([key, change]) => ({ rowIndex: Number(key), change }))
    .filter((item) => item.change.kind === 'insert')
    .sort((a, b) => a.rowIndex - b.rowIndex)
  for (const { rowIndex, change } of inserts) {
    const record: GridRow = { __rownum: zhCN.edit.newRow, __idx: rowIndex }
    for (let ci = 0; ci < cols.length; ci += 1) {
      const value = change.values?.[cols[ci].name]
      record[`c${ci}`] = value === undefined ? null : value
    }
    rows.push(record)
  }
  return rows
}

/** 该行主键列的当前值快照（保存时用于生成 WHERE） */
function pkSnapshot(data: Record<string, unknown>): Record<string, string | null> {
  const cols = visibleColumns(props.columns, props.maxColumns)
  const snapshot: Record<string, string | null> = {}
  for (const name of props.editable?.primaryKey ?? []) {
    const ci = cols.findIndex((c) => c.name === name)
    if (ci >= 0) snapshot[name] = (data[`c${ci}`] as string | null) ?? null
  }
  return snapshot
}

/** 指定行的主键值快照（删除行时用于生成 WHERE） */
function pkOfRow(rowIndex: number): Record<string, string | null> | null {
  if (!table) return null
  const row = table.getRows().find((r) => Number(r.getData().__idx ?? Number.NaN) === rowIndex)
  return row ? pkSnapshot(row.getData() as Record<string, unknown>) : null
}

/** 当前选中的全局行号（整行选中或单元格选区所在行） */
function selectedRowIndexes(): number[] {
  if (!table) return []
  const indexes = new Set<number>()
  for (const row of table.getSelectedRows()) {
    const idx = Number(row.getData().__idx ?? Number.NaN)
    if (!Number.isNaN(idx)) indexes.add(idx)
  }
  for (const range of table.getRanges()) {
    for (const cells of range.getStructuredCells()) {
      for (const cell of cells) {
        const idx = Number(cell.getRow().getData().__idx ?? Number.NaN)
        if (!Number.isNaN(idx)) indexes.add(idx)
      }
    }
  }
  return [...indexes]
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

defineExpose({ copy, selectedRowIndexes, hasSelection, pkOfRow })

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
    // spec-16：待删除行淡红 + 删除线；新增行左侧蓝色标记条
    rowFormatter: (row: RowComponent) => {
      const rowIndex = Number(row.getData().__idx ?? Number.NaN)
      const change = props.changes?.[rowIndex]
      const element = row.getElement()
      element.classList.toggle('joyo-row-deleted', change?.kind === 'delete')
      element.classList.toggle('joyo-row-inserted', change?.kind === 'insert')
    },
  })

  // 双击进入编辑态：锁定态仅为复制文本（回车/Esc 退出后还原原值，不写库）；
  // 解锁态下主键列与二进制列不进入编辑态
  table.on('cellDblClick', (_event: Event, cell: CellComponent) => {
    const col = columnOfField(cell.getField())
    if (props.unlocked && col && !canEdit(col)) return
    void cell.edit(true)
  })
  table.on('cellEdited', (cell: CellComponent) => {
    const oldValue = (cell.getOldValue() as string | null | undefined) ?? null
    if (!props.unlocked) {
      // 只读语义：还原原值（原值为 null 时保持 NULL 展示）
      cell.setValue(oldValue, false)
      return
    }
    const col = columnOfField(cell.getField())
    const rowIndex = Number(cell.getRow().getData().__idx ?? Number.NaN)
    if (!col || Number.isNaN(rowIndex)) return
    const raw = cell.getValue() as string | null
    // spec-16：全大写 NULL 关键字 = SQL NULL，其余（空串/小写 null）为字面值
    const value = raw === null ? null : parseCellInput(String(raw))
    cell.setValue(value, false)
    emit('edit', {
      rowIndex,
      column: col.name,
      value,
      original: oldValue,
      pk: pkSnapshot(cell.getRow().getData()),
    })
  })
  table.on('cellContext', (event: Event, _cell: CellComponent) => {
    const mouse = event as MouseEvent
    emit('context', { x: mouse.clientX, y: mouse.clientY, hasSelection: hasSelection() })
  })

  // spec-16：选区变化同步给面板（"删除行"按钮可用态）
  const syncSelection = (): void => emit('selection', hasSelection())
  table.on('rowSelected', syncSelection)
  table.on('rowDeselected', syncSelection)
  table.on('rangeChanged', syncSelection)
  table.on('cellClick', syncSelection)

  hostRef.value.addEventListener('keydown', onKeydown)
  hostRef.value.addEventListener('contextmenu', onContextMenu)
})

// 列结构变化（列/上限/解锁态）才重建列，避免改动单元格时列宽与滚动被重置
watch(
  () => [props.columns, props.maxColumns, props.unlocked],
  () => {
    if (!table) return
    table.setColumns(buildColumns())
  },
)

watch(
  () => [props.columns, props.rows, props.page, props.pageSize, props.maxColumns, props.changes],
  () => {
    if (!table) return
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
