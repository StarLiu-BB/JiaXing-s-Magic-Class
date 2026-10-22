import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import { recoverFromExpiredLogin, setSessionExpiredHandler } from '@/utils/session'

vi.mock('@/router', () => ({ default: { push: vi.fn(), replace: vi.fn() } }))
vi.mock('element-plus', () => ({ ElMessage: vi.fn(), ElMessageBox: { confirm: vi.fn() } }))
const auth = vi.hoisted(() => ({ token: '' }))
vi.mock('@/utils/auth', () => ({ getToken: () => auth.token }))

beforeEach(() => { vi.clearAllMocks(); auth.token = '' })

describe('登录过期恢复', () => {
  it('业务 401 和 HTTP 401 同步清全部身份并只跳转一次', async () => {
    const state = { token: 'old', roles: ['ADMIN'], permissions: ['course:edit'] }
    let finish
    const router = { currentRoute: { value: { path: '/course/list' } }, replace: vi.fn(() => new Promise(resolve => { finish = resolve })) }
    const store = { resetToken: vi.fn(() => { Object.assign(state, { token: '', roles: [], permissions: [] }) }) }
    setSessionExpiredHandler(() => recoverFromExpiredLogin(store, router))
    const failed = request.interceptors.response.handlers[0]
    await expect(failed.fulfilled({ data: { code: 401, msg: 'expired' } })).rejects.toThrow('expired')
    expect(state).toEqual({ token: '', roles: [], permissions: [] })
    await expect(failed.rejected({ message: 'http401', response: { status: 401 } })).rejects.toBeTruthy()
    expect(store.resetToken).toHaveBeenCalledTimes(1)
    expect(router.replace).toHaveBeenCalledWith('/login')
    finish()
    await Promise.resolve()
  })

  it('旧令牌请求迟到的 401 不清除重新登录后的新会话', async () => {
    auth.token = 'old'
    const sent = request.interceptors.request.handlers[0].fulfilled({ headers: {} })
    auth.token = 'new'
    const recovered = vi.fn(async () => {})
    setSessionExpiredHandler(recovered)
    const failed = request.interceptors.response.handlers[0]
    await expect(failed.fulfilled({ config: sent, data: { code: 401 } })).rejects.toThrow()
    await expect(failed.rejected({ config: sent, message: 'old401', response: { status: 401 } })).rejects.toBeTruthy()
    expect(recovered).not.toHaveBeenCalled()
  })

  it('已在登录页也清身份但不重复导航', async () => {
    const store = { resetToken: vi.fn() }
    const router = { currentRoute: { value: { path: '/login' } }, replace: vi.fn() }
    await recoverFromExpiredLogin(store, router)
    expect(store.resetToken).toHaveBeenCalledOnce()
    expect(router.replace).not.toHaveBeenCalled()
  })
})
