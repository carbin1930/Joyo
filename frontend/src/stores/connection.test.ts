import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const ipcMock = vi.hoisted(() => ({
  list: vi.fn(),
  save: vi.fn(),
  remove: vi.fn(),
  decryptPassword: vi.fn(),
}))

vi.mock('@/api/ipc', () => ({ connectionsIpc: ipcMock }))

const httpMock = vi.hoisted(() => ({
  openSession: vi.fn(),
  closeSession: vi.fn(),
}))

vi.mock('@/api/connection', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/connection')>()
  return { ...actual, openSession: httpMock.openSession, closeSession: httpMock.closeSession }
})

import { useConnectionStore } from './connection'

const conn = {
  id: 'conn-1',
  name: '本地库',
  host: '127.0.0.1',
  port: 3306,
  username: 'root',
  defaultDatabase: '',
  sshTunnel: null,
  createdAt: '2026-10-15T00:00:00Z',
  updatedAt: '2026-10-15T00:00:00Z',
  hasPassword: true,
}

describe('connection store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('load 拉取连接列表', async () => {
    ipcMock.list.mockResolvedValue([conn])
    const store = useConnectionStore()
    await store.load()
    expect(store.items).toHaveLength(1)
    expect(store.items[0].name).toBe('本地库')
  })

  it('connect：有密码 → 解密并建立会话，绿点生效', async () => {
    ipcMock.list.mockResolvedValue([conn])
    ipcMock.decryptPassword.mockResolvedValue('secret')
    httpMock.openSession.mockResolvedValue({
      connectionId: 'conn-1',
      serverVersion: '8.0.36',
      defaultDatabase: '',
    })

    const store = useConnectionStore()
    await store.load()
    const ok = await store.connect('conn-1')

    expect(ok).toBe(true)
    expect(ipcMock.decryptPassword).toHaveBeenCalledWith('conn-1')
    expect(httpMock.openSession).toHaveBeenCalledWith('conn-1', {
      host: '127.0.0.1',
      port: 3306,
      username: 'root',
      password: 'secret',
      defaultDatabase: '',
    })
    expect(store.isConnected('conn-1')).toBe(true)
    expect(store.sessions['conn-1']?.serverVersion).toBe('8.0.36')
  })

  it('connect：无保存密码 → 不调接口，提示先编辑', async () => {
    ipcMock.list.mockResolvedValue([{ ...conn, hasPassword: false }])
    const store = useConnectionStore()
    await store.load()

    const ok = await store.connect('conn-1')

    expect(ok).toBe(false)
    expect(ipcMock.decryptPassword).not.toHaveBeenCalled()
    expect(httpMock.openSession).not.toHaveBeenCalled()
  })

  it('connect：会话建立失败 → 不进入已连接集合', async () => {
    ipcMock.list.mockResolvedValue([conn])
    ipcMock.decryptPassword.mockResolvedValue('secret')
    httpMock.openSession.mockRejectedValue(new Error('用户名或密码错误'))

    const store = useConnectionStore()
    await store.load()
    const ok = await store.connect('conn-1')

    expect(ok).toBe(false)
    expect(store.isConnected('conn-1')).toBe(false)
  })

  it('disconnect：调用断开并清除绿点与会话信息', async () => {
    ipcMock.list.mockResolvedValue([conn])
    ipcMock.decryptPassword.mockResolvedValue('secret')
    httpMock.openSession.mockResolvedValue({
      connectionId: 'conn-1',
      serverVersion: '8.0.36',
      defaultDatabase: '',
    })
    httpMock.closeSession.mockResolvedValue(null)

    const store = useConnectionStore()
    await store.load()
    await store.connect('conn-1')
    await store.disconnect('conn-1')

    expect(httpMock.closeSession).toHaveBeenCalledWith('conn-1')
    expect(store.isConnected('conn-1')).toBe(false)
    expect(store.sessions['conn-1']).toBeUndefined()
  })

  it('remove：已连接的连接先断开再删除', async () => {
    ipcMock.list.mockResolvedValueOnce([conn]) // 首次 load
    ipcMock.list.mockResolvedValue([]) // 删除后的 load
    ipcMock.decryptPassword.mockResolvedValue('secret')
    httpMock.openSession.mockResolvedValue({
      connectionId: 'conn-1',
      serverVersion: '8.0.36',
      defaultDatabase: '',
    })
    ipcMock.remove.mockResolvedValue(true)

    const store = useConnectionStore()
    await store.load()
    await store.connect('conn-1')
    await store.remove('conn-1')

    expect(httpMock.closeSession).toHaveBeenCalledWith('conn-1')
    expect(ipcMock.remove).toHaveBeenCalledWith('conn-1')
    expect(store.items).toHaveLength(0)
  })
})
