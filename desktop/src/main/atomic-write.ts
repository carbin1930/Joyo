import fs from 'node:fs'

/**
 * JSON 原子写（history-store / secure-store 共用，data-model.md 持久化约定）：
 * 先写临时文件再 rename 覆盖，避免半写文件。
 * Windows 下目标文件可能被杀毒/索引器瞬时锁定导致 rename EPERM，做短重试兜底。
 */
export function atomicWriteJson(filePath: string, data: unknown, indent?: number): void {
  const tmpPath = `${filePath}.tmp`
  fs.writeFileSync(tmpPath, JSON.stringify(data, null, indent), 'utf-8')
  let lastError: unknown
  for (let attempt = 0; attempt < 4; attempt++) {
    try {
      fs.renameSync(tmpPath, filePath)
      return
    } catch (e) {
      lastError = e
      const code = (e as NodeJS.ErrnoException).code
      if (code !== 'EPERM' && code !== 'EACCES') break
      sleepSync(50 * (attempt + 1))
    }
  }
  throw lastError
}

/** 同步休眠（Atomics.wait 在 Node 主线程可用；毫秒级等待，同步持久化路径内可接受） */
function sleepSync(ms: number): void {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms)
}
