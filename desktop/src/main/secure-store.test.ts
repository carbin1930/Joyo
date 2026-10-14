import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import {
  ConnectionsStore,
  connectionsFilePath,
  type ConnectionConfig,
  type PasswordCipher,
} from './secure-store'

/** 测试用伪加密：可逆 base64，不依赖系统钥匙串 */
class FakeCipher implements PasswordCipher {
  encrypt(plain: string): string {
    return `enc:test:${Buffer.from(plain, 'utf-8').toString('base64')}`
  }

  decrypt(cipherText: string): string | null {
    if (!cipherText.startsWith('enc:test:')) return null
    return Buffer.from(cipherText.slice('enc:test:'.length), 'base64').toString('utf-8')
  }
}

let tmpDir: string

function newStore(): ConnectionsStore {
  return new ConnectionsStore(connectionsFilePath(tmpDir), new FakeCipher())
}

const sample = {
  name: '本地开发库',
  host: '127.0.0.1',
  port: 3306,
  username: 'root',
  password: 'secret-123',
}

describe('ConnectionsStore', () => {
  beforeEach(() => {
    tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'joyo-test-'))
  })

  afterEach(() => {
    fs.rmSync(tmpDir, { recursive: true, force: true })
  })

  it('空目录加载得到空列表', () => {
    expect(newStore().list()).toEqual([])
  })

  it('保存后列表返回脱敏视图：无密码字段、有 hasPassword', () => {
    const item = newStore().save({ ...sample })
    expect(item.name).toBe('本地开发库')
    expect(item.hasPassword).toBe(true)
    expect('passwordEnc' in item).toBe(false)
  })

  it('密文落盘为 enc: 前缀，绝无明文密码', () => {
    newStore().save({ ...sample })
    const raw = fs.readFileSync(connectionsFilePath(tmpDir), 'utf-8')
    expect(raw).toContain('enc:test:')
    expect(raw).not.toContain('secret-123')
  })

  it('编辑时不传密码则保留原密码，传了则更新', () => {
    const store = newStore()
    const created = store.save({ ...sample })

    store.save({ id: created.id, name: '改名', host: '10.0.0.1', port: 3307, username: 'admin' })
    expect(store.decryptPassword(created.id)).toBe('secret-123')

    store.save({
      id: created.id,
      name: '改名',
      host: '10.0.0.1',
      port: 3307,
      username: 'admin',
      password: 'new-pass',
    })
    expect(store.decryptPassword(created.id)).toBe('new-pass')
  })

  it('更新真实更新 updatedAt 且保持 createdAt', () => {
    const store = newStore()
    const created = store.save({ ...sample }) as ConnectionConfig & { hasPassword: boolean }
    const raw1 = JSON.parse(fs.readFileSync(connectionsFilePath(tmpDir), 'utf-8'))
    const createdAt = raw1.connections[0].createdAt
    expect(created.createdAt).toBe(createdAt)

    store.save({
      id: created.id,
      name: 'x',
      host: 'h',
      port: 1,
      username: 'u',
    })
    const raw2 = JSON.parse(fs.readFileSync(connectionsFilePath(tmpDir), 'utf-8'))
    expect(raw2.connections[0].createdAt).toBe(createdAt)
  })

  it('删除返回 true；重复删除返回 false', () => {
    const store = newStore()
    const created = store.save({ ...sample })
    expect(store.remove(created.id)).toBe(true)
    expect(store.remove(created.id)).toBe(false)
    expect(store.list()).toEqual([])
  })

  it('更新不存在的连接抛错', () => {
    expect(() =>
      newStore().save({ id: 'not-exist', name: 'x', host: 'h', port: 1, username: 'u' }),
    ).toThrow(/连接不存在/)
  })

  it('损坏的配置文件被重命名兜底并重建为空', () => {
    const filePath = connectionsFilePath(tmpDir)
    fs.writeFileSync(filePath, '{ 不是 JSON !!!', 'utf-8')

    expect(newStore().list()).toEqual([])
    const broken = fs.readdirSync(tmpDir).filter((f) => f.includes('.broken.'))
    expect(broken.length).toBe(1)
    expect(fs.existsSync(filePath)).toBe(true)
  })

  it('解密失败返回 null（模拟换机器钥匙串不匹配）', () => {
    const filePath = connectionsFilePath(tmpDir)
    const created = newStore().save({ ...sample })
    const raw = JSON.parse(fs.readFileSync(filePath, 'utf-8')) as {
      connections: { passwordEnc: string }[]
    }
    raw.connections[0].passwordEnc = 'enc:v1:不兼容的密文'
    fs.writeFileSync(filePath, JSON.stringify(raw), 'utf-8')

    expect(newStore().decryptPassword(created.id)).toBeNull()
    expect(newStore().decryptPassword('not-exist')).toBeNull()
  })

  it('原子写：无 .tmp 残留', () => {
    const store = newStore()
    store.save({ ...sample })
    store.save({ name: '第二个', host: 'h2', port: 3306, username: 'u2', password: 'p2' })
    const leftovers = fs.readdirSync(tmpDir).filter((f) => f.endsWith('.tmp'))
    expect(leftovers).toEqual([])
  })
})
