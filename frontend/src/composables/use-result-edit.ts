import type { EditableInfo, MutationOperation, MutationRequest } from '@/types/api'

/**
 * 表数据编辑的纯逻辑（spec-16）：本地脏标记、变更摘要、请求负载与语句预览。
 * 不含任何 store / 组件依赖，便于单测。
 *
 * 行号约定：
 * - 已有行用**全局行号**（0 起）= (page-1)*pageSize + 页内序号，翻页不丢改动
 * - 新增行用**负数**行号（-1、-2…），与已有行天然不冲突
 */

export type ChangeKind = 'insert' | 'update' | 'delete'

export interface RowChange {
  kind: ChangeKind
  /** 主键列名 → 原值（新增行无） */
  pk?: Record<string, string | null>
  /** 列名 → 新值（删除行无）；null 表示 SQL NULL */
  values?: Record<string, string | null>
}

export interface EditState {
  unlocked: boolean
  changes: Record<number, RowChange>
  /** 已分配的新增行序号（行号 = -insertSeq） */
  insertSeq: number
}

export interface ChangeSummary {
  insert: number
  update: number
  delete: number
}

export function createState(): EditState {
  return { unlocked: false, changes: {}, insertSeq: 0 }
}

export function isDirty(state: EditState): boolean {
  return Object.keys(state.changes).length > 0
}

export function summarize(state: EditState): ChangeSummary {
  const summary: ChangeSummary = { insert: 0, update: 0, delete: 0 }
  for (const change of Object.values(state.changes)) {
    summary[change.kind] += 1
  }
  return summary
}

/** SQL NULL 约定：编辑输入全大写关键字 NULL 视为 NULL，其余（含空串、小写 null）为字面值 */
export function parseCellInput(text: string): string | null {
  return text === 'NULL' ? null : text
}

/**
 * 记录单元格修改。新增行直接写值；已有行与原值相同则撤销该格的改动
 * （某行所有改动都被撤销时整行改动移除）。
 */
export function setCell(
  state: EditState,
  rowIndex: number,
  column: string,
  value: string | null,
  original: string | null,
  pk?: Record<string, string | null>,
): void {
  if (rowIndex < 0) {
    const change = state.changes[rowIndex] ?? { kind: 'insert' as const, values: {} }
    change.values = { ...(change.values ?? {}), [column]: value }
    state.changes[rowIndex] = change
    return
  }

  const existing = state.changes[rowIndex]
  if (value === original) {
    if (!existing || existing.kind !== 'update' || !existing.values) return
    const next = { ...existing.values }
    delete next[column]
    if (Object.keys(next).length === 0) {
      delete state.changes[rowIndex]
    } else {
      existing.values = next
    }
    return
  }

  const change: RowChange = existing?.kind === 'update'
    ? existing
    : { kind: 'update', pk: pk ?? existing?.pk ?? {}, values: {} }
  change.values = { ...(change.values ?? {}), [column]: value }
  if (pk) change.pk = pk
  state.changes[rowIndex] = change
}

/** 标记删除：新增行被删则直接撤销新增；已有行转为删除变更 */
export function markDeleted(
  state: EditState,
  rowIndex: number,
  pk?: Record<string, string | null>,
): void {
  if (rowIndex < 0) {
    delete state.changes[rowIndex]
    return
  }
  state.changes[rowIndex] = { kind: 'delete', pk: pk ?? state.changes[rowIndex]?.pk ?? {} }
}

/** 追加一个新增行，返回其行号（负数） */
export function addInsertRow(state: EditState): number {
  state.insertSeq += 1
  const rowIndex = -state.insertSeq
  state.changes[rowIndex] = { kind: 'insert', values: {} }
  return rowIndex
}

export function discard(state: EditState): void {
  state.changes = {}
  state.insertSeq = 0
}

/**
 * 生成保存负载：insert → update → delete 顺序；缺少主键值的改动直接抛错
 * （说明结果已失效，应重新执行查询）。
 */
export function buildPayload(
  state: EditState,
  editable: EditableInfo,
  database?: string,
): MutationRequest {
  const entries = Object.entries(state.changes)
    .map(([key, change]) => ({ rowIndex: Number(key), change }))
    .sort((a, b) => orderOf(a.change.kind) - orderOf(b.change.kind) || a.rowIndex - b.rowIndex)

  const operations: MutationOperation[] = entries.map(({ change }) => {
    if (change.kind === 'insert') {
      return { type: 'insert', values: change.values ?? {} }
    }
    const pk = change.pk ?? {}
    if (Object.keys(pk).length === 0) {
      throw new Error('缺少主键值，请重新执行查询后再保存')
    }
    return change.kind === 'delete'
      ? { type: 'delete', pk }
      : { type: 'update', pk, values: change.values ?? {} }
  })

  return {
    database: database ?? editable.schema,
    table: editable.table,
    primaryKey: editable.primaryKey,
    operations,
  }
}

/** 语句预览（仅用于保存确认弹窗展示，真正执行的语句由后端参数化生成） */
export function previewStatements(state: EditState, editable: EditableInfo): string[] {
  const table = quoteIdent(editable.schema) + '.' + quoteIdent(editable.table)
  return Object.entries(state.changes)
    .map(([key, change]) => ({ rowIndex: Number(key), change }))
    .sort((a, b) => orderOf(a.change.kind) - orderOf(b.change.kind) || a.rowIndex - b.rowIndex)
    .map(({ change }) => {
      if (change.kind === 'insert') {
        const values = change.values ?? {}
        const columns = Object.keys(values)
        return `INSERT INTO ${table} (${columns.map(quoteIdent).join(', ')}) VALUES (${columns
          .map((c) => literal(values[c] ?? null))
          .join(', ')})`
      }
      const where = Object.entries(change.pk ?? {})
        .map(([col, value]) => `${quoteIdent(col)} = ${literal(value ?? null)}`)
        .join(' AND ')
      if (change.kind === 'delete') return `DELETE FROM ${table} WHERE ${where}`
      const set = Object.entries(change.values ?? {})
        .map(([col, value]) => `${quoteIdent(col)} = ${literal(value ?? null)}`)
        .join(', ')
      return `UPDATE ${table} SET ${set} WHERE ${where}`
    })
}

function orderOf(kind: ChangeKind): number {
  return kind === 'insert' ? 0 : kind === 'update' ? 1 : 2
}

function quoteIdent(name: string): string {
  return '`' + name.replace(/`/g, '``') + '`'
}

function literal(value: string | null): string {
  return value === null ? 'NULL' : `'${value.replace(/'/g, "''")}'`
}
