import { defineStore } from 'pinia'
import { connectionsIpc } from '@/api/ipc'
import { closeSession, openSession } from '@/api/connection'
import { toast } from '@/composables/use-toast'
import { useSchemaStore } from '@/stores/schema'
import type { ConnectionListItem, SaveConnectionInput } from '@/types/ipc'
import type { SessionInfo, SshTunnelPayload } from '@/api/connection'
import { zhCN } from '@/locales/zh-CN'

/**
 * 连接管理状态（spec-01）：
 * - items：连接列表（Main 进程持久化的脱敏视图）
 * - selectedId：侧栏单击选中
 * - connectedIds：已建立会话的连接（绿点）
 * - sessions：会话信息（服务器版本等，spec-02 复用）
 */
export const useConnectionStore = defineStore('connection', {
  state: () => ({
    items: [] as ConnectionListItem[],
    selectedId: null as string | null,
    connectedIds: [] as string[],
    sessions: {} as Record<string, SessionInfo>,
    loading: false,
  }),

  getters: {
    selected: (state): ConnectionListItem | null =>
      state.items.find((c) => c.id === state.selectedId) ?? null,
    isConnected: (state) => (id: string) => state.connectedIds.includes(id),
  },

  actions: {
    async load(): Promise<void> {
      this.loading = true
      try {
        this.items = await connectionsIpc.list()
      } catch (e) {
        toast('error', String(e instanceof Error ? e.message : e))
      } finally {
        this.loading = false
      }
    },

    async save(input: SaveConnectionInput): Promise<ConnectionListItem> {
      const saved = await connectionsIpc.save(input)
      await this.load()
      this.selectedId = saved.id
      return saved
    },

    /** 删除：若处于连接态先断开会话（spec-01 行为细节 4） */
    async remove(id: string): Promise<void> {
      if (this.isConnected(id)) {
        await this.disconnect(id)
      }
      await connectionsIpc.remove(id)
      if (this.selectedId === id) this.selectedId = null
      await this.load()
    },

    /** 双击连接：有保存的密码则解密并建立会话；无密码提示先编辑（spec-01 行为细节 3） */
    async connect(id: string): Promise<boolean> {
      const conn = this.items.find((c) => c.id === id)
      if (!conn) return false
      if (this.isConnected(id)) return true

      if (!conn.hasPassword) {
        toast('error', zhCN.connection.needPassword)
        return false
      }

      const password = await connectionsIpc.decryptPassword(id)
      if (password === null) {
        toast('error', zhCN.connection.passwordInvalid)
        return false
      }

      // spec-06：启用 SSH 隧道时解密对应 secret（明文仅经回环传给后端）
      let sshTunnel: SshTunnelPayload | undefined
      const ssh = conn.sshTunnel
      if (ssh?.enabled) {
        if (ssh.authType === 'password') {
          const sshPassword = await connectionsIpc.decryptPassword(id, 'sshPassword')
          if (sshPassword === null) {
            toast('error', zhCN.connection.passwordInvalid)
            return false
          }
          sshTunnel = {
            enabled: true,
            host: ssh.host,
            port: ssh.port,
            username: ssh.username,
            authType: 'password',
            password: sshPassword,
          }
        } else {
          const passphrase = await connectionsIpc.decryptPassword(id, 'sshPassphrase')
          if (passphrase === null) {
            toast('error', zhCN.connection.passwordInvalid)
            return false
          }
          sshTunnel = {
            enabled: true,
            host: ssh.host,
            port: ssh.port,
            username: ssh.username,
            authType: 'privateKey',
            privateKeyPath: ssh.privateKeyPath,
            passphrase: passphrase ?? '',
          }
        }
      }

      try {
        const session = await openSession(id, {
          host: conn.host,
          port: conn.port,
          username: conn.username,
          password,
          defaultDatabase: conn.defaultDatabase,
          sshTunnel,
        })
        this.sessions[id] = session
        this.connectedIds.push(id)
        toast('success', zhCN.connection.connected(session.serverVersion))
        // 连接成功 → 激活该连接的对象树（spec-02）
        void useSchemaStore().activate(id)
        return true
      } catch (e) {
        toast('error', e instanceof Error ? e.message : String(e))
        return false
      }
    },

    async disconnect(id: string): Promise<void> {
      try {
        await closeSession(id)
      } finally {
        this.connectedIds = this.connectedIds.filter((c) => c !== id)
        delete this.sessions[id]
        // 断开 → 清空对应对象树（spec-02 行为细节 6）
        useSchemaStore().deactivate(id)
      }
    },

    select(id: string): void {
      this.selectedId = id
    },
  },
})
