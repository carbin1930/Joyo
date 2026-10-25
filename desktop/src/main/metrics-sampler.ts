import type { BrowserWindow, ProcessMetric } from 'electron'

/**
 * 资源监控采样器（spec-14，PRD F14）：
 * 每 3s 汇总 Electron 全部子进程 + 拉取 /health 的 JVM 指标 → 推送 metrics:tick。
 * app.getAppMetrics 经参数注入，保持模块可在 Node 测试环境加载。
 */

export interface JvmInfo {
  heapUsedMb: number | null
  heapMaxMb: number | null
  cpuLoad: number | null
}

export interface MetricsTick {
  backendUp: boolean
  electron: { processCount: number; memMb: number; cpuPercent: number }
  jvm: JvmInfo | null
}

export interface HealthSnapshot {
  code?: number
  data?: { jvm?: JvmInfo | null } | null
}

const SAMPLER_INTERVAL_MS = 3_000
const FAILS_TO_OFFLINE = 2

/** 汇总 Electron 全部子进程：内存 workingSetSize 求和 KB→MB；CPU percentCPUUsage 求和 */
export function summarizeElectron(metrics: ProcessMetric[]): MetricsTick['electron'] {
  let memKb = 0
  let cpu = 0
  for (const m of metrics) {
    memKb += m.memory.workingSetSize
    cpu += m.cpu.percentCPUUsage
  }
  return {
    processCount: metrics.length,
    memMb: Math.round(memKb / 1024),
    cpuPercent: Math.round(cpu * 10) / 10,
  }
}

/** /health 拉取函数工厂（2s 超时，带 token 鉴权） */
export function createHealthFetcher(baseUrl: string, token: string): () => Promise<HealthSnapshot> {
  return async () => {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 2_000)
    try {
      const resp = await fetch(`${baseUrl}/api/v1/health`, {
        headers: { 'X-Joyo-Token': token },
        signal: controller.signal,
      })
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
      return (await resp.json()) as HealthSnapshot
    } finally {
      clearTimeout(timer)
    }
  }
}

/**
 * 启动采样器（后端就绪后调用，spec 第 4 节行为 1），返回 stop 函数。
 * /health 失败：保持上次 JVM 数据 1 个周期，连续失败 2 次置离线（spec 第 5 节）。
 */
export function startMetricsSampler(
  getWindow: () => BrowserWindow | null,
  getAppMetrics: () => ProcessMetric[],
  fetchHealth: () => Promise<HealthSnapshot>,
): () => void {
  let failCount = 0
  let lastJvm: JvmInfo | null = null

  const tick = async (): Promise<void> => {
    const electron = summarizeElectron(getAppMetrics())
    try {
      const health = await fetchHealth()
      if (health.data?.jvm) lastJvm = health.data.jvm
      failCount = 0
    } catch {
      failCount += 1
      if (failCount >= FAILS_TO_OFFLINE) lastJvm = null
    }
    const win = getWindow()
    if (!win || win.isDestroyed()) return
    win.webContents.send('metrics:tick', {
      backendUp: failCount < FAILS_TO_OFFLINE,
      electron,
      jvm: failCount >= FAILS_TO_OFFLINE ? null : lastJvm,
    } satisfies MetricsTick)
  }

  void tick()
  const timer = setInterval(() => void tick(), SAMPLER_INTERVAL_MS)
  return () => clearInterval(timer)
}
