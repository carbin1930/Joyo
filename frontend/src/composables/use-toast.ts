import { reactive } from 'vue'

/**
 * 全局 toast（coding-frontend.md 第 6 节）：右下角提示，error 级别默认 5 秒。
 * 渲染由 <ToastHost /> 承担（挂在 App.vue）。
 */

export type ToastType = 'info' | 'success' | 'error'

export interface ToastItem {
  id: number
  type: ToastType
  message: string
}

const state = reactive<{ items: ToastItem[] }>({ items: [] })
let nextId = 1

function show(message: string, type: ToastType, durationMs: number): number {
  const id = nextId++
  state.items.push({ id, type, message })
  if (durationMs > 0) {
    setTimeout(() => dismiss(id), durationMs)
  }
  return id
}

export function dismiss(id: number): void {
  const index = state.items.findIndex((t) => t.id === id)
  if (index >= 0) state.items.splice(index, 1)
}

/** 统一"类型在前"调用约定：toast('error', '失败原因')；error 级别默认 5 秒 */
export function toast(type: ToastType, message: string): number {
  return show(message, type, type === 'error' ? 5000 : 3000)
}

export function useToastState() {
  return state
}
