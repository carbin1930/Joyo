import { describe, expect, it } from 'vitest'
import {
  addInsertRow,
  buildPayload,
  createState,
  discard,
  isDirty,
  markDeleted,
  parseCellInput,
  previewStatements,
  setCell,
  summarize,
} from './use-result-edit'
import type { EditableInfo } from '@/types/api'

const editable: EditableInfo = { schema: 'db1', table: 'users', primaryKey: ['id'] }

describe('use-result-edit', () => {
  it('修改单元格生成 update 变更，改回原值则撤销', () => {
    const state = createState()
    setCell(state, 0, 'name', 'Bob', 'Alice', { id: '7' })
    expect(state.changes[0]).toEqual({ kind: 'update', pk: { id: '7' }, values: { name: 'Bob' } })

    setCell(state, 0, 'name', 'Alice', 'Alice', { id: '7' })
    expect(state.changes[0]).toBeUndefined()
    expect(isDirty(state)).toBe(false)
  })

  it('同一行多列改动合并为一条 update', () => {
    const state = createState()
    setCell(state, 3, 'name', 'Bob', 'Alice', { id: '7' })
    setCell(state, 3, 'age', '30', '20', { id: '7' })

    expect(state.changes[3].values).toEqual({ name: 'Bob', age: '30' })
    expect(summarize(state)).toEqual({ insert: 0, update: 1, delete: 0 })
  })

  it('新增行用负数行号，删除新增行即撤销', () => {
    const state = createState()
    const rowIndex = addInsertRow(state)
    expect(rowIndex).toBe(-1)

    setCell(state, rowIndex, 'name', 'New', null)
    expect(state.changes[rowIndex]).toEqual({ kind: 'insert', values: { name: 'New' } })

    markDeleted(state, rowIndex)
    expect(state.changes[rowIndex]).toBeUndefined()
  })

  it('删除已有行记录主键原值', () => {
    const state = createState()
    markDeleted(state, 5, { id: '9' })

    expect(state.changes[5]).toEqual({ kind: 'delete', pk: { id: '9' } })
    expect(summarize(state)).toEqual({ insert: 0, update: 0, delete: 1 })
  })

  it('NULL 关键字解析为 null，空串保持空串', () => {
    expect(parseCellInput('NULL')).toBeNull()
    expect(parseCellInput('null')).toBe('null')
    expect(parseCellInput('')).toBe('')
  })

  it('负载按 新增→修改→删除 排序并带主键', () => {
    const state = createState()
    setCell(state, 2, 'name', 'Bob', 'Alice', { id: '3' })
    markDeleted(state, 4, { id: '5' })
    const insertIndex = addInsertRow(state)
    setCell(state, insertIndex, 'name', 'New', null)

    const payload = buildPayload(state, editable)

    expect(payload).toEqual({
      database: 'db1',
      table: 'users',
      primaryKey: ['id'],
      operations: [
        { type: 'insert', values: { name: 'New' } },
        { type: 'update', pk: { id: '3' }, values: { name: 'Bob' } },
        { type: 'delete', pk: { id: '5' } },
      ],
    })
  })

  it('缺主键值的改动抛错（提示重新执行查询）', () => {
    const state = createState()
    markDeleted(state, 4)

    expect(() => buildPayload(state, editable)).toThrow('缺少主键值')
  })

  it('语句预览可直接阅读（引号转义、NULL 裸值）', () => {
    const state = createState()
    setCell(state, 1, 'name', "O'Brien", 'x', { id: '2' })
    setCell(state, 1, 'age', null, '20', { id: '2' })
    markDeleted(state, 2, { id: '3' })

    const statements = previewStatements(state, editable)

    expect(statements[0]).toBe("UPDATE `db1`.`users` SET `name` = 'O''Brien', `age` = NULL WHERE `id` = '2'")
    expect(statements[1]).toBe("DELETE FROM `db1`.`users` WHERE `id` = '3'")
  })

  it('放弃改动清空全部变更', () => {
    const state = createState()
    setCell(state, 0, 'name', 'Bob', 'Alice', { id: '1' })
    addInsertRow(state)
    discard(state)

    expect(isDirty(state)).toBe(false)
    expect(summarize(state)).toEqual({ insert: 0, update: 0, delete: 0 })
  })
})
