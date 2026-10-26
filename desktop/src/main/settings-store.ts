import fs from 'node:fs'
import path from 'node:path'
import { atomicWriteJson } from './atomic-write'

/**
 * 全局设置持久化（spec-18/F10，data-model.md 持久化约定）：
 * - 只在 Electron Main 读写（渲染层无 fs 权限），经 IPC settings:get / settings:set 暴露
 * - 原子写（tmp + rename）；文件缺失或损坏时回退默认值，不抛错
 * 本模块不依赖 electron，便于纯 node 单元测试。
 */

export interface AppSettings {
  theme: 'light' | 'dark'
  /** 界面基准字号（网格与主要文本） */
  fontSize: number
  /** 查询默认行数上限 */
  maxRows: number
  /** 查询超时（秒） */
  queryTimeoutSec: number
}

export const DEFAULT_SETTINGS: AppSettings = {
  theme: 'light',
  fontSize: 13,
  maxRows: 1000,
  queryTimeoutSec: 30,
}

const MIN_FONT_SIZE = 12
const MAX_FONT_SIZE = 18
const MIN_MAX_ROWS = 100
const MAX_MAX_ROWS = 10000
const MIN_TIMEOUT = 5
const MAX_TIMEOUT = 600

export function settingsFilePath(userDataDir: string): string {
  return path.join(userDataDir, 'settings.json')
}

function clamp(value: number, min: number, max: number, fallback: number): number {
  if (!Number.isFinite(value)) return fallback
  return Math.min(max, Math.max(min, Math.round(value)))
}

/** 逐字段归一：未知/越界值回退默认，保证渲染层拿到的永远合法 */
export function normalize(input: Partial<AppSettings> | null | undefined): AppSettings {
  const raw = input ?? {}
  return {
    theme: raw.theme === 'dark' ? 'dark' : 'light',
    fontSize: clamp(raw.fontSize as number, MIN_FONT_SIZE, MAX_FONT_SIZE, DEFAULT_SETTINGS.fontSize),
    maxRows: clamp(raw.maxRows as number, MIN_MAX_ROWS, MAX_MAX_ROWS, DEFAULT_SETTINGS.maxRows),
    queryTimeoutSec: clamp(
      raw.queryTimeoutSec as number,
      MIN_TIMEOUT,
      MAX_TIMEOUT,
      DEFAULT_SETTINGS.queryTimeoutSec,
    ),
  }
}

export class SettingsStore {
  private readonly filePath: string

  constructor(filePath: string) {
    this.filePath = filePath
  }

  /** 读取设置：文件缺失/损坏 → 默认值（并落盘一份默认，便于用户直接改文件） */
  load(): AppSettings {
    if (!fs.existsSync(this.filePath)) {
      return DEFAULT_SETTINGS
    }
    try {
      const text = fs.readFileSync(this.filePath, 'utf-8')
      return normalize(JSON.parse(text) as Partial<AppSettings>)
    } catch (e) {
      console.error('[settings] 读取设置失败，回退默认值', e)
      return DEFAULT_SETTINGS
    }
  }

  /** 合并保存（部分更新）；返回保存后的完整设置 */
  save(patch: Partial<AppSettings>): AppSettings {
    const next = normalize({ ...this.load(), ...patch })
    atomicWriteJson(this.filePath, next, 2)
    return next
  }
}
