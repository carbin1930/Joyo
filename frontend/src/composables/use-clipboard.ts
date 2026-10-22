/** 单元格原始值：后端一律传字符串，SQL NULL 为 null */
export type CellValue = string | null | undefined

/** BLOB/BINARY 类列：不展示也不复制二进制内容（spec-04 第 2 节） */
const BINARY_TYPE_PATTERN = /BLOB|BINARY/i

export function isBinaryType(type: string | undefined): boolean {
  return type ? BINARY_TYPE_PATTERN.test(type) : false
}

/** 单元格展示/复制文本：NULL → 空串，二进制列 → (BLOB) */
export function formatCell(value: CellValue, type?: string): string {
  if (value === null || value === undefined) return ''
  return isBinaryType(type) ? '(BLOB)' : value
}

/**
 * TSV：制表符分隔，CRLF 换行，Excel 可直接粘贴。
 * NULL 复制为空串（spec-04 第 4 节）。
 */
export function toTsv(rows: CellValue[][], types?: string[]): string {
  return rows
    .map((row) => row.map((value, ci) => formatCell(value, types?.[ci])).join('\t'))
    .join('\r\n')
}

/** CSV 字段转义：含逗号/引号/换行时加引号，内部引号翻倍（RFC 4180） */
export function escapeCsvField(field: string): string {
  return /[",\r\n]/.test(field) ? `"${field.replace(/"/g, '""')}"` : field
}

/**
 * CSV：可选表头（列名）；行内 NULL → 空字段。
 */
export function toCsv(
  rows: CellValue[][],
  options?: { header?: string[]; types?: string[] },
): string {
  const lines: string[] = []
  if (options?.header && options.header.length > 0) {
    lines.push(options.header.map(escapeCsvField).join(','))
  }
  for (const row of rows) {
    lines.push(
      row
        .map((value, ci) => escapeCsvField(formatCell(value, options?.types?.[ci])))
        .join(','),
    )
  }
  return lines.join('\r\n')
}

/** 写入系统剪贴板；不可用（无权限/非安全上下文）时返回 false，由调用方提示 */
export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}
