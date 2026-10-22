import { describe, expect, it } from 'vitest'
import type { ColumnMeta } from '@/types/api'
import {
  MAX_RENDERED_COLUMNS,
  hiddenColumnCount,
  toGridRows,
  visibleColumns,
} from './use-result-grid'

function makeColumns(count: number): ColumnMeta[] {
  return Array.from({ length: count }, (_, i) => ({
    name: `col${i}`,
    type: 'VARCHAR',
    nullable: true,
  }))
}

describe('use-result-grid：列截断', () => {
  it('超过 50 列只渲染前 50 列并给出隐藏数（spec-04 第 5 节）', () => {
    const columns = makeColumns(53)
    expect(visibleColumns(columns)).toHaveLength(MAX_RENDERED_COLUMNS)
    expect(hiddenColumnCount(columns)).toBe(3)
    expect(hiddenColumnCount(makeColumns(10))).toBe(0)
  })
})

describe('use-result-grid：行数据转换', () => {
  it('行号跨页连续，缺列补 null，值保持字符串', () => {
    const rows = toGridRows(
      [
        ['1', 'alice'],
        ['9007199254740993'],
      ],
      3,
      3,
      100,
    )

    expect(rows[0]).toEqual({ __rownum: 201, c0: '1', c1: 'alice', c2: null })
    // BIGINT 精度红线：原样保留字符串
    expect(rows[1].c0).toBe('9007199254740993')
    expect(rows[1].c1).toBe(null)
    expect(rows[1].c2).toBe(null)
  })

  it('首页行号从 1 开始', () => {
    expect(toGridRows([['a']], 1, 1, 100)[0].__rownum).toBe(1)
  })
})
