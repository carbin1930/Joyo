import { createWriteStream } from 'node:fs'
import { rename, rm, stat } from 'node:fs/promises'
import { Readable } from 'node:stream'
import { pipeline } from 'node:stream/promises'

/**
 * 导出下载器（spec-15 第 3 节）：Main 拉取后端 /export 文件流写盘。
 * 先写 <目标>.part，成功后 rename，杜绝半成品残留为正式文件；
 * 途中失败删除 .part 后抛错。
 */

export interface ExportJob {
  connectionId: string
  sql: string
  format: 'csv' | 'insert' | 'xlsx'
  database?: string
  tableName?: string
  rowLimit?: number
}

type FetchLike = (url: string, init: RequestInit) => Promise<Response>

export async function downloadExport(
  fetchFn: FetchLike,
  baseUrl: string,
  token: string,
  targetPath: string,
  job: ExportJob,
): Promise<number> {
  let resp: Response
  try {
    resp = await fetchFn(`${baseUrl}/api/v1/export`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json;charset=UTF-8', 'X-Joyo-Token': token },
      body: JSON.stringify(job),
    })
  } catch (e) {
    throw new Error(`无法连接后端: ${e instanceof Error ? e.message : String(e)}`)
  }
  if (!resp.ok) {
    throw new Error(`后端响应异常: HTTP ${resp.status}`)
  }
  // 项目约定：HTTP 恒 200 + 业务码 JSON；导出错走 JSON（开流前失败）
  const contentType = resp.headers.get('content-type') ?? ''
  if (contentType.includes('application/json')) {
    const payload = (await resp.json()) as { code?: number; message?: string }
    throw new Error(payload.message ?? `导出失败（code=${payload.code}）`)
  }
  if (!resp.body) {
    throw new Error('后端未返回文件内容')
  }

  const partPath = `${targetPath}.part`
  try {
    await pipeline(Readable.fromWeb(resp.body as never), createWriteStream(partPath))
    await rename(partPath, targetPath)
  } catch (e) {
    await rm(partPath, { force: true })
    throw e
  }
  return (await stat(targetPath)).size
}