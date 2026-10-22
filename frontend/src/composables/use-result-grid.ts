import type { ColumnMeta } from '@/types/api'

/** 网格最多渲染列数（spec-04 第 5 节）；超出部分由状态条提示 */
export const MAX_RENDERED_COLUMNS = 50

/** 实际参与渲染的列（截断到上限） */
export function visibleColumns(
  columns: ColumnMeta[],
  max = MAX_RENDERED_COLUMNS,
): ColumnMeta[] {
  return columns.slice(0, Math.max(0, max))
}

/** 未显示列数（状态条提示用） */
export function hiddenColumnCount(
  columns: ColumnMeta[],
  max = MAX_RENDERED_COLUMNS,
): number {
  return Math.max(0, columns.length - max)
}

/** Tabulator 行记录：键为 c0..cN，行号为 __rownum */
export type GridRow = Record<string, string | null | number>

/**
 * 二维字符串行 → Tabulator 记录。
 * 行号按页连续（第 2 页首行 = pageSize + 1）；缺列补 null，保持与列数对齐。
 */
export function toGridRows(
  rows: (string | null)[][],
  columnCount: number,
  page: number,
  pageSize: number,
): GridRow[] {
  const offset = (Math.max(1, page) - 1) * Math.max(0, pageSize)
  return rows.map((row, ri) => {
    const record: GridRow = { __rownum: offset + ri + 1 }
    for (let ci = 0; ci < columnCount; ci += 1) {
      record[`c${ci}`] = row[ci] === undefined ? null : row[ci]
    }
    return record
  })
}
