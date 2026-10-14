import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, BackendUnavailableError, request, resetApiConfig } from './http'

function mockFetchOnce(payload: unknown, status = 200): void {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify(payload), { status })),
  )
}

describe('api/http 请求层', () => {
  beforeEach(() => {
    resetApiConfig()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('code=0 时返回 data 并注入 X-Joyo-Token 请求头', async () => {
    const fetchMock = vi.fn(
      async (_url: string | URL, _init?: RequestInit) =>
        new Response(JSON.stringify({ code: 0, message: 'ok', data: { v: 1 } }), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const data = await request<{ v: number }>('GET', '/api/v1/demo')

    expect(data).toEqual({ v: 1 })
    const [url, init] = fetchMock.mock.calls[0]
    const headers = (init as RequestInit).headers as Record<string, string>
    expect(headers['X-Joyo-Token']).toBe('dev-token')
    expect(url).toBe('http://127.0.0.1:18080/api/v1/demo')
    expect((init as RequestInit).method).toBe('GET')
  })

  it('code!=0 时抛出携带错误码的 ApiError', async () => {
    mockFetchOnce({ code: 1002, message: '资源不存在', data: null })

    await expect(request('GET', '/api/v1/demo')).rejects.toMatchObject({
      name: 'ApiError',
      code: 1002,
      message: '资源不存在',
    })
  })

  it('网络不可达时抛出 BackendUnavailableError', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => Promise.reject(new Error('ECONNREFUSED'))))

    await expect(request('GET', '/api/v1/demo')).rejects.toBeInstanceOf(BackendUnavailableError)
  })

  it('HTTP 非 200 时抛出 BackendUnavailableError', async () => {
    mockFetchOnce({}, 503)

    await expect(request('GET', '/api/v1/demo')).rejects.toBeInstanceOf(BackendUnavailableError)
  })

  it('POST 请求体序列化为 JSON', async () => {
    const fetchMock = vi.fn(
      async (_url: string | URL, _init?: RequestInit) =>
        new Response(JSON.stringify({ code: 0, message: 'ok', data: null }), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await request('POST', '/api/v1/demo', { a: 1 })

    const [, init] = fetchMock.mock.calls[0]
    expect((init as RequestInit).method).toBe('POST')
    expect((init as RequestInit).body).toBe('{"a":1}')
  })

  it('ApiError 是 Error 的子类且包含错误码', () => {
    const err = new ApiError(3001, 'SQL 语法错误')
    expect(err).toBeInstanceOf(Error)
    expect(err.code).toBe(3001)
  })
})
