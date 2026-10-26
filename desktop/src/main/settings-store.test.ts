import { afterEach, describe, expect, it } from 'vitest'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { DEFAULT_SETTINGS, SettingsStore, normalize } from './settings-store'

/** 每个用例用独立临时目录，避免相互污染 */
const dirs: string[] = []

function tempStore(): SettingsStore {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'joyo-settings-'))
  dirs.push(dir)
  return new SettingsStore(path.join(dir, 'settings.json'))
}

afterEach(() => {
  while (dirs.length > 0) {
    const dir = dirs.pop()
    if (dir) fs.rmSync(dir, { recursive: true, force: true })
  }
})

describe('settings-store', () => {
  it('无文件时返回默认设置', () => {
    expect(tempStore().load()).toEqual(DEFAULT_SETTINGS)
  })

  it('保存后可读回且跨实例持久', () => {
    const store = tempStore()
    const saved = store.save({ theme: 'dark', fontSize: 15 })

    expect(saved.theme).toBe('dark')
    expect(saved.fontSize).toBe(15)
    // 保留未改动的字段
    expect(saved.maxRows).toBe(DEFAULT_SETTINGS.maxRows)
    expect(new SettingsStore((store as unknown as { filePath: string }).filePath).load().theme).toBe('dark')
  })

  it('损坏文件回退默认不抛错', () => {
    const store = tempStore()
    fs.writeFileSync((store as unknown as { filePath: string }).filePath, '{ not json', 'utf-8')

    expect(store.load()).toEqual(DEFAULT_SETTINGS)
  })

  it('越界与非法值被夹到合法区间', () => {
    const settings = normalize({ fontSize: 99, maxRows: 1, queryTimeoutSec: 10000, theme: 'x' as never })

    expect(settings.fontSize).toBe(18)
    expect(settings.maxRows).toBe(100)
    expect(settings.queryTimeoutSec).toBe(600)
    expect(settings.theme).toBe('light')
  })
})
