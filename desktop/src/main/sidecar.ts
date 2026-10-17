import { spawn, type ChildProcess } from 'node:child_process'
import net from 'node:net'
import { randomUUID } from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'

/**
 * Sidecar 进程管理唯一模块（docs/10-architecture/overview.md 第 2 节）。
 * 职责：空闲端口扫描 → 随机 Token → 拉起 Java 后端 → 健康检查轮询 → 退出清理。
 * 其他模块不得直接操作后端进程。
 */

export interface ResolvedBackend {
  baseUrl: string
  token: string
  /** 停止后端（杀掉进程树）；开发直连模式下为空操作 */
  stop(): Promise<void>
}

const HEALTH_TIMEOUT_MS = 30_000
const HEALTH_INTERVAL_MS = 500

/** 扫描一个空闲 TCP 端口（存在竞态，但配合健康检查重试足够可靠） */
export function getFreePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const server = net.createServer()
    server.listen(0, '127.0.0.1', () => {
      const address = server.address()
      if (address && typeof address === 'object') {
        const port = address.port
        server.close(() => resolve(port))
      } else {
        server.close()
        reject(new Error('获取空闲端口失败'))
      }
    })
    server.on('error', reject)
  })
}

function findJava(explicitRuntimeDir: string | undefined): string {
  // 优先使用应用内置 JRE（打包时由 jlink 生成，见 overview.md 第 5 节）
  const candidates: string[] = []
  if (explicitRuntimeDir) {
    const exe = process.platform === 'win32' ? 'java.exe' : 'java'
    candidates.push(path.join(explicitRuntimeDir, 'bin', exe))
  }
  candidates.push('java')
  for (const candidate of candidates) {
    try {
      fs.accessSync(candidate, fs.constants.X_OK)
      return candidate
    } catch {
      // 继续尝试下一个候选
    }
  }
  return 'java'
}

/** 单次健康检查，2 秒超时 */
async function probeHealth(baseUrl: string, token: string): Promise<boolean> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 2_000)
  try {
    const resp = await fetch(`${baseUrl}/api/v1/health`, {
      headers: { 'X-Joyo-Token': token },
      signal: controller.signal,
    })
    if (!resp.ok) return false
    const payload = (await resp.json()) as { code?: number }
    return payload.code === 0
  } catch {
    return false
  } finally {
    clearTimeout(timer)
  }
}

/** 轮询健康检查直到就绪或超时 */
export async function waitForHealthy(baseUrl: string, token: string): Promise<void> {
  const deadline = Date.now() + HEALTH_TIMEOUT_MS
  while (Date.now() < deadline) {
    if (await probeHealth(baseUrl, token)) return
    await new Promise((r) => setTimeout(r, HEALTH_INTERVAL_MS))
  }
  throw new Error(`后端健康检查超时（${HEALTH_TIMEOUT_MS / 1000}s）: ${baseUrl}`)
}

async function killProcessTree(child: ChildProcess): Promise<void> {
  if (child.pid === undefined || child.exitCode !== null) return
  const pid = child.pid
  await new Promise<void>((resolve) => {
    if (process.platform === 'win32') {
      // Windows 必须杀进程树，否则残留 java 子进程
      const killer = spawn('taskkill', ['/pid', String(pid), '/T', '/F'], { windowsHide: true })
      killer.on('close', () => resolve())
      killer.on('error', () => resolve())
    } else {
      child.kill('SIGTERM')
      resolve()
    }
  })
}

export interface StartSidecarOptions {
  jarPath: string
  /** 内置 JRE 目录（可选），存在则优先使用 */
  runtimeDir?: string
  /** 后端文件日志路径（传入则设置 JOYO_LOG_FILE 环境变量） */
  logFile?: string
  onLog?: (line: string) => void
}

/** 拉起 Java 后端 Sidecar 并等待就绪 */
export async function startSidecar(options: StartSidecarOptions): Promise<ResolvedBackend> {
  const port = await getFreePort()
  const token = randomUUID()
  const javaPath = findJava(options.runtimeDir)
  const baseUrl = `http://127.0.0.1:${port}`

  const log = options.onLog ?? ((line: string) => console.log(`[backend] ${line}`))
  const child = spawn(
    javaPath,
    ['-jar', options.jarPath, `--server.port=${port}`, `--joyo.token=${token}`],
    {
      stdio: ['ignore', 'pipe', 'pipe'],
      windowsHide: true,
      env: options.logFile
        ? { ...process.env, JOYO_LOG_FILE: options.logFile }
        : process.env,
    },
  )

  child.stdout?.on('data', (chunk: Buffer) => log(chunk.toString().trimEnd()))
  child.stderr?.on('data', (chunk: Buffer) => log(chunk.toString().trimEnd()))
  child.on('exit', (code) => log(`后端进程退出: code=${code}`))

  try {
    await waitForHealthy(baseUrl, token)
  } catch (e) {
    await killProcessTree(child)
    throw e
  }

  return {
    baseUrl,
    token,
    stop: () => killProcessTree(child),
  }
}

/**
 * 解析后端连接方式：
 * - 开发模式（JOYO_DEV_BACKEND_URL 已设置）：直连手动启动的后端，不 spawn
 * - 打包模式（isPackaged=true）：spawn 内置 jar（resources/bin/joyo-backend.jar）；
 *   开发模式下也可用 JOYO_BACKEND_JAR 覆盖 jar 路径来测试 sidecar 流程
 */
export async function resolveBackend(isPackaged: boolean, logFile?: string): Promise<ResolvedBackend> {
  const devUrl = process.env.JOYO_DEV_BACKEND_URL
  if (devUrl) {
    const baseUrl = devUrl.replace(/\/$/, '')
    return {
      baseUrl,
      token: process.env.JOYO_DEV_TOKEN ?? 'dev-token',
      stop: async () => {},
    }
  }

  const jarPath =
    process.env.JOYO_BACKEND_JAR ??
    (isPackaged
      ? path.join(process.resourcesPath, 'bin', 'joyo-backend.jar')
      : path.join(__dirname, '../../../backend/target/joyo-backend.jar'))

  if (!fs.existsSync(jarPath)) {
    throw new Error(
      `找不到后端 jar: ${jarPath}\n开发模式请设置 JOYO_DEV_BACKEND_URL，` +
        `或先执行 mvn package 并用 JOYO_BACKEND_JAR 指定路径`,
    )
  }

  const runtimeDir = process.env.JOYO_BACKEND_RUNTIME
  return startSidecar({ jarPath, runtimeDir, logFile })
}
