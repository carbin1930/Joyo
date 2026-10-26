import { defineStore } from 'pinia'
import type { AppSettings } from '@/types/ipc'

/** 无 Electron 环境（纯浏览器开发/单测）时的默认设置 */
const FALLBACK: AppSettings = {
  theme: 'light',
  fontSize: 13,
  maxRows: 1000,
  queryTimeoutSec: 30,
}

/**
 * 全局设置（spec-18/F10）：
 * - 持久化在 Electron Main（userData/settings.json），渲染层经 IPC 读写
 * - 主题用 <html data-theme> 切换 CSS 变量；字号写入 --joyo-font-size
 * - 行数上限与超时同步给查询 store（由 QueryEditorView 读取）
 */
export const useSettingsStore = defineStore('settings', {
  state: () => ({
    settings: { ...FALLBACK } as AppSettings,
    loaded: false,
  }),

  actions: {
    async load(): Promise<void> {
      try {
        this.settings = (await window.joyo?.getSettings()) ?? FALLBACK
      } catch {
        this.settings = { ...FALLBACK }
      }
      this.loaded = true
      this.apply()
    },

    /** 合并保存：先落盘（Main 为唯一真源），成功后再更新本地并应用 */
    async save(patch: Partial<AppSettings>): Promise<void> {
      const next = await window.joyo?.setSettings(patch)
      this.settings = next ?? { ...this.settings, ...patch }
      this.apply()
    },

    /** 应用到 DOM：主题属性 + 字号变量 */
    apply(): void {
      const root = document.documentElement
      root.dataset.theme = this.settings.theme
      root.style.setProperty('--joyo-font-size', `${this.settings.fontSize}px`)
    },
  },
})
