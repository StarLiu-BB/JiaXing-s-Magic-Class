import http from 'node:http'
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'
import { ApiError, createRequest } from '@/utils/request'

function json(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify(body))
}

const routes = {
  '/ok': (req, res) => json(res, 200, { code: 200, msg: 'ok', data: { auth: req.headers.authorization || null } }),
  '/biz-error': (req, res) => json(res, 200, { code: 409, msg: '上一个回答还在生成' }),
  '/body-401': (req, res) => json(res, 200, { code: 401, msg: '未登录或令牌已失效' }),
  '/http-401': (req, res) => json(res, 401, { code: 401, msg: '令牌无效或已过期' }),
  '/http-500': (req, res) => json(res, 500, { code: 500, msg: '系统异常' })
}

let server
let baseURL

beforeAll(async () => {
  server = http.createServer((req, res) => (routes[req.url] || ((q, r) => json(r, 404, {})))(req, res))
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  baseURL = `http://127.0.0.1:${server.address().port}`
})

afterAll(() => new Promise((resolve) => {
  server.closeAllConnections()
  server.close(resolve)
}))

const make = (overrides = {}) => createRequest({ baseURL, getToken: () => '', onUnauthorized: vi.fn(), ...overrides })

describe('createRequest', () => {
  it('带上令牌，成功时直接返回 data', async () => {
    await expect(make({ getToken: () => 't-1' })({ url: '/ok' })).resolves.toEqual({ auth: 'Bearer t-1' })
  })

  it('业务错误是 HTTP 200 + body.code，以 body.code 为准', async () => {
    const error = await make()({ url: '/biz-error' }).catch((e) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect([error.code, error.message]).toEqual([409, '上一个回答还在生成'])
  })

  it.each(['/body-401', '/http-401'])('%s：视为登录失效', async (url) => {
    const onUnauthorized = vi.fn()
    const error = await make({ onUnauthorized })({ url }).catch((e) => e)
    expect(error.code).toBe(401)
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('skipAuthRedirect：401 交给调用方处理，不触发全局跳转', async () => {
    const onUnauthorized = vi.fn()
    const error = await make({ onUnauthorized })({ url: '/http-401', skipAuthRedirect: true }).catch((e) => e)
    expect(error.code).toBe(401)
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('HTTP 错误带上服务端的说明', async () => {
    const error = await make()({ url: '/http-500' }).catch((e) => e)
    expect([error.code, error.message]).toEqual([500, '系统异常'])
  })

  it('连不上服务：提示网络问题', async () => {
    const request = createRequest({ baseURL: 'http://127.0.0.1:1', getToken: () => '', onUnauthorized: vi.fn() })
    const error = await request({ url: '/ok' }).catch((e) => e)
    expect([error.code, error.message]).toEqual([0, '网络连接失败，请检查网络'])
  })
})
