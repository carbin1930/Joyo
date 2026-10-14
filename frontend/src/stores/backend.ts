import { defineStore } from 'pinia'
import { getHealth } from '@/api/system'
import { BackendUnavailableError } from '@/api/http'
import type { HealthData } from '@/types/api'

export type BackendStatus = 'checking' | 'ok' | 'unavailable'

/** 后端服务状态：App 级心跳与异常遮罩的数据来源 */
export const useBackendStore = defineStore('backend', {
  state: () => ({
    status: 'checking' as BackendStatus,
    info: null as HealthData | null,
    errorMessage: '' as string,
  }),
  actions: {
    async check(): Promise<void> {
      this.status = 'checking'
      try {
        this.info = await getHealth()
        this.status = 'ok'
        this.errorMessage = ''
      } catch (e) {
        this.status = 'unavailable'
        this.errorMessage =
          e instanceof BackendUnavailableError ? e.message : `后端异常: ${String(e)}`
      }
    },
  },
})
