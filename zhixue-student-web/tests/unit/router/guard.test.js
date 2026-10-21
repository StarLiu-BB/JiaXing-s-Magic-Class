import { describe, expect, it, vi } from 'vitest'
import { ID_PATTERN, createAuthGuard, safeRedirect } from '@/router/guard'

const route = (fullPath, meta = {}) => ({ fullPath, meta })
const user = (overrides = {}) => ({
  isLoggedIn: true,
  profile: { userId: 1003 },
  loadProfile: vi.fn().mockResolvedValue({ userId: 1003 }),
  reset: vi.fn(),
  ...overrides
})

describe('createAuthGuard', () => {
  it('登录页等公开页面直接放行', async () => {
    expect(await createAuthGuard(() => user({ isLoggedIn: false }))(route('/login', { public: true }))).toBe(true)
  })

  it('未登录：去登录页并记住要回到的地址', async () => {
    const guard = createAuthGuard(() => user({ isLoggedIn: false, profile: null }))
    expect(await guard(route('/chat/2112366523950714882'))).toEqual({
      name: 'login',
      query: { redirect: '/chat/2112366523950714882' }
    })
  })

  it('已取过用户信息：直接放行，不重复请求', async () => {
    const u = user()
    expect(await createAuthGuard(() => u)(route('/chat'))).toBe(true)
    expect(u.loadProfile).not.toHaveBeenCalled()
  })

  it('有令牌但还没取用户信息：先取再放行', async () => {
    const u = user({ profile: null })
    expect(await createAuthGuard(() => u)(route('/chat'))).toBe(true)
    expect(u.loadProfile).toHaveBeenCalledOnce()
  })

  it('令牌失效（取用户信息返回 401）：清掉登录态，去登录页', async () => {
    const expired = Object.assign(new Error('令牌无效'), { code: 401 })
    const u = user({ profile: null, loadProfile: vi.fn().mockRejectedValue(expired) })
    expect(await createAuthGuard(() => u)(route('/chat/1'))).toEqual({ name: 'login', query: { redirect: '/chat/1' } })
    expect(u.reset).toHaveBeenCalledOnce()
  })

  it.each([
    ['网络不通或超时', 0],
    ['网关 502', 502]
  ])('取用户信息失败但不是登录失效（%s）：不清登录态，提示后放行', async (_, code) => {
    const notify = vi.fn()
    const failure = Object.assign(new Error('网络连接失败，请检查网络'), { code })
    const u = user({ profile: null, loadProfile: vi.fn().mockRejectedValue(failure) })
    expect(await createAuthGuard(() => u, notify)(route('/chat/1'))).toBe(true)
    expect(u.reset).not.toHaveBeenCalled()
    expect(notify).toHaveBeenCalledWith('warning', '网络连接失败，请检查网络')
  })
})

describe('路径参数校验', () => {
  it.each([
    ['2112366523950714882', true],
    ['9001', true],
    ['abc', false],
    ['12abc', false],
    ['-1', false],
    ['21123665239507148820', false]
  ])('%s → %s', (value, expected) => {
    expect(ID_PATTERN.test(value)).toBe(expected)
  })

  it.each(['/chat/abc', '/chat/12x'])('地址参数不合法时回到新对话：%s', async (path) => {
    const notify = vi.fn()
    const guard = createAuthGuard(() => user(), notify)
    const result = await guard({ fullPath: path, meta: {}, params: { id: path.split('/')[2] } })
    expect(result).toBe('/chat')
    expect(notify).toHaveBeenCalledWith('warning', expect.stringContaining('地址不正确'))
  })

  it('合法 ID 正常放行', async () => {
    expect(await createAuthGuard(() => user())({ fullPath: '/chat/9001', meta: {}, params: { id: '9001' } })).toBe(true)
  })
})

describe('safeRedirect', () => {
  it.each([
    ['/chat/1', '/chat/1'],
    ['/course/2007?lesson=4024', '/course/2007?lesson=4024'],
    ['//evil.example.com', '/chat'],
    ['https://evil.example.com', '/chat'],
    ['/login?redirect=/chat', '/chat'],
    [undefined, '/chat'],
    [['/chat/1'], '/chat']
  ])('%j → %s', (input, expected) => {
    expect(safeRedirect(input)).toBe(expected)
  })
})
