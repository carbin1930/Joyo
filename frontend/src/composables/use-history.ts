import type { HistoryEntryInput } from '@/types/ipc'

/**
 * 查询历史写入（spec-05 统一入口）：
 * 页面中**任何会执行 SQL 的操作**都走这里记录，便于回溯"什么时候、在哪个库、改了什么"。
 *
 * 约定 kind：
 * - `SELECT` / `UPDATE` / `DDL` / `MULTI`：编辑器执行 SQL（多语句为 MULTI）
 * - `ERROR`：执行失败（原 SQL 无法归类的历史写法，保留兼容）
 * - `EDIT`：结果网格编辑保存（spec-16，含生成的 INSERT/UPDATE/DELETE）
 * - `EXPORT`：导出（spec-15，服务端为导出重跑一次 SQL）
 * - `SYNC_COMPARE` / `SYNC`：表/数据同步的比对与执行（spec-17）
 *
 * 写入失败只告警，不影响业务主流程（node 单测环境无 window，用 globalThis 访问避免未声明引用）。
 */
export async function recordHistory(entry: Omit<HistoryEntryInput, 'executedAt'>): Promise<void> {
  try {
    await globalThis.window?.joyo?.history?.append({
      ...entry,
      executedAt: new Date().toISOString(),
    })
  } catch (e) {
    console.warn('[history] 记录查询历史失败', e)
  }
}
