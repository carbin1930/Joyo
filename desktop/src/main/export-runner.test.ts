import { afterEach, describe, expect, it } from 'vitest'
import { readFile, rm } from 'node:fs/promises'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import { downloadExport, type ExportJob } from './export-runner'

/** 起一次性本地 HTTP 服务，返回 baseUrl 与关闭函数 */
function startServer(handler: (req: http.IncomingMessage, res: http.ServerResponse) => void): Promise<{ baseUrl: string; close: () => void }> {
  return new Promise((resolve) => {
    const server = http.createServer(handler)
    server.listen(0, '127.0.0.1', () => {
      const address = server.address()
      const port = address && typeof address === 'object' ? address.port : 0
      resolve({ baseUrl: `http://127.0.0.1:${port}`, close: () => server.close() })
    })
  })
}

const job: ExportJob = { connectionId: 'c1', sql: 'SELECT 1', format: 'csv' }
let cleanup: (() => void) | null = null
afterEach(() => {
  cleanup?.()
  cleanup = null
})

describe('downloadExport', () => {
  it('文件流写入目标路径并返回字节数', async () => {
    const server = await startServer((req, res) => {
      res.setHeader('Content-Type', 'text/csv;charset=UTF-8')
      res.end('id,name\r\n1,alice\r\n')
    })
    cleanup = server.close
    const target = path.join(os.tmpdir(), `joyo-export-test-${Date.now()}.csv`)

    const bytes = await downloadExport(fetch, server.baseUrl, 't', target, job)

    expect(bytes).toBe(18)
    expect(await readFile(target, 'utf-8')).toBe('id,name\r\n1,alice\r\n')
    await rm(target, { force: true })
  })

  it('JSON 业务错误抛出且不产生文件', async () => {
    const server = await startServer((req, res) => {
      res.setHeader('Content-Type', 'application/json')
      res.end(JSON.stringify({ code: 3001, message: 'SQL 执行错误' }))
    })
    cleanup = server.close
    const target = path.join(os.tmpdir(), `joyo-export-test-${Date.now()}.csv`)

    await expect(downloadExport(fetch, server.baseUrl, 't', target, job)).rejects.toThrow('SQL 执行错误')
    await expect(readFile(target)).rejects.toThrow()
    await rm(`${target}.part`, { force: true })
  })

  it('流中途断开删除 .part 并抛错', async () => {
    const server = await startServer((req, res) => {
      res.setHeader('Content-Type', 'text/csv')
      res.write('id,name\r\n')
      setTimeout(() => res.destroy(), 20)
    })
    cleanup = server.close
    const target = path.join(os.tmpdir(), `joyo-export-test-${Date.now()}.csv`)

    await expect(downloadExport(fetch, server.baseUrl, 't', target, job)).rejects.toThrow()
    await expect(readFile(`${target}.part`)).rejects.toThrow()
    await expect(readFile(target)).rejects.toThrow()
  })
})