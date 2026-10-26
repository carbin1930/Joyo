import type { BrowserWindow } from 'electron'

/**
 * 自动更新封装（spec-19/F11）：检查 → 下载进度 → 安装重启。
 *
 * 说明：electron-updater 采用**运行时动态加载**而非静态 import——
 * 开发环境/未安装该依赖时不会因模块缺失而崩溃，直接降级为"不可用"状态。
 * 类型因此不使用 electron-updater 的静态类型（保持编译与运行时解耦）。
 */

export type UpdatePhase =
  | 'idle'
  | 'checking'
  | 'available'
  | 'not-available'
  | 'downloading'
  | 'downloaded'
  | 'error'
  | 'unsupported'

export interface UpdateState {
  phase: UpdatePhase
  version?: string
  notes?: string
  percent?: number
  message?: string
}

/** 动态加载 electron-updater 的 autoUpdater（失败返回 null → 降级） */
async function loadAutoUpdater(): Promise<Record<string, unknown> | null> {
  try {
    const specifier = 'electron-updater'
    const mod = (await import(specifier)) as { autoUpdater?: unknown }
    return (mod.autoUpdater as Record<string, unknown>) ?? null
  } catch (e) {
    console.warn('[updater] electron-updater 不可用，自动更新已降级', e)
    return null
  }
}

function asString(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined
}

export class UpdateManager {
  private readonly getWindow: () => BrowserWindow | null
  private state: UpdateState = { phase: 'idle' }
  private lastNotifiedPercent = -1

  constructor(getWindow: () => BrowserWindow | null) {
    this.getWindow = getWindow
  }

  current(): UpdateState {
    return this.state
  }

  /** 推送状态给渲染层（download-progress 按 5% 节流，避免高频 IPC） */
  private push(next: UpdateState, throttle = false): void {
    if (throttle && next.phase === 'downloading') {
      const step = Math.floor((next.percent ?? 0) / 5)
      if (step === this.lastNotifiedPercent) return
      this.lastNotifiedPercent = step
    }
    this.state = next
    const win = this.getWindow()
    if (win && !win.isDestroyed()) {
      win.webContents.send('updater:event', next)
    }
  }

  /** 检查更新；未打包环境直接返回 unsupported */
  async check(isPackaged: boolean): Promise<UpdateState> {
    if (!isPackaged) {
      this.push({ phase: 'unsupported', message: '开发环境不支持更新' })
      return this.state
    }
    const autoUpdater = await loadAutoUpdater()
    if (!autoUpdater) {
      this.push({ phase: 'unsupported', message: '自动更新组件不可用' })
      return this.state
    }

    this.push({ phase: 'checking' })
    try {
      const result = (await (autoUpdater.checkForUpdates as () => Promise<unknown>)()) as
        | { updateInfo?: { version?: string; releaseNotes?: string | unknown[] } }
        | null
      if (!result) {
        this.push({ phase: 'not-available' })
        return this.state
      }
      const info = result.updateInfo ?? {}
      const notes = Array.isArray(info.releaseNotes)
        ? info.releaseNotes.map((n) => asString((n as { note?: unknown }).note) ?? '').join('\n')
        : asString(info.releaseNotes)
      this.push({
        phase: 'available',
        version: info.version,
        notes: notes ?? '',
      })
    } catch (e) {
      this.push({ phase: 'error', message: e instanceof Error ? e.message : String(e) })
    }
    return this.state
  }

  /** 注册下载进度与完成事件（在 check 前调用一次即可） */
  async bindEvents(): Promise<void> {
    const autoUpdater = await loadAutoUpdater()
    if (!autoUpdater) return
    const on = autoUpdater.on as ((event: string, cb: (...args: never[]) => void) => void) | undefined
    if (typeof on !== 'function') return

    on('download-progress', (...args: never[]) => {
      const progress = args[0] as { percent?: number }
      this.push({ phase: 'downloading', percent: Math.round(progress.percent ?? 0) }, true)
    })
    on('update-downloaded', (...args: never[]) => {
      const info = args[0] as { version?: string }
      this.push({ phase: 'downloaded', version: asString(info.version) })
    })
    on('error', (...args: never[]) => {
      const error = args[0] as Error | string | undefined
      this.push({ phase: 'error', message: error instanceof Error ? error.message : String(error ?? '') })
    })
  }

  /** 退出并安装（仅 downloaded 状态有意义） */
  async install(): Promise<void> {
    const autoUpdater = await loadAutoUpdater()
    if (!autoUpdater) return
    const quitAndInstall = autoUpdater.quitAndInstall as (() => void) | undefined
    if (typeof quitAndInstall === 'function') quitAndInstall()
  }
}
