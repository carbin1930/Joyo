import { describe, expect, it } from 'vitest'
import { escapeCsvField, formatCell, isBinaryType, toCsv, toTsv } from './use-clipboard'

describe('use-clipboard：单元格文本', () => {
  it('NULL 转为空串，与空字符串在复制结果中一致（spec-04 第 4 节）', () => {
    expect(formatCell(null)).toBe('')
    expect(formatCell(undefined)).toBe('')
    expect(formatCell('')).toBe('')
  })

  it('二进制列只输出占位，不复制原始内容', () => {
    expect(isBinaryType('BLOB')).toBe(true)
    expect(isBinaryType('LONGBLOB')).toBe(true)
    expect(isBinaryType('VARBINARY')).toBe(true)
    expect(isBinaryType('VARCHAR')).toBe(false)
    expect(formatCell('\u0000\u0001', 'BLOB')).toBe('(BLOB)')
    expect(formatCell('hello', 'VARCHAR')).toBe('hello')
  })
})

describe('use-clipboard：TSV', () => {
  it('制表符分隔、CRLF 换行，NULL 为空字段', () => {
    const text = toTsv(
      [
        ['1', 'alice', null],
        ['2', '', 'x'],
      ],
      ['BIGINT', 'VARCHAR', 'VARCHAR'],
    )
    expect(text).toBe('1\talice\t\r\n2\t\tx')
  })
})

describe('use-clipboard：CSV', () => {
  it('含逗号/引号/换行的字段加引号并翻倍内部引号（RFC 4180）', () => {
    expect(escapeCsvField('plain')).toBe('plain')
    expect(escapeCsvField('a,b')).toBe('"a,b"')
    expect(escapeCsvField('say "hi"')).toBe('"say ""hi"""')
    expect(escapeCsvField('line1\nline2')).toBe('"line1\nline2"')
  })

  it('可选表头 + NULL 空字段 + 二进制列占位', () => {
    const text = toCsv(
      [
        ['1', 'a,b', null],
        ['2', 'x', 'blobbytes'],
      ],
      { header: ['id', 'name', 'note'], types: ['BIGINT', 'VARCHAR', 'BLOB'] },
    )
    expect(text).toBe('id,name,note\r\n1,"a,b",\r\n2,x,(BLOB)')
  })
})
