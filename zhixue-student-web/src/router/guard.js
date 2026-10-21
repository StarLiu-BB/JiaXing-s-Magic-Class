/** 19 位以内纯数字：雪花 ID 在 URL 里的合法形式。不合法时按「不存在」处理，不去请求后端 */
export const ID_PATTERN = /^\d{1,19}$/

/**
 * 登录后要回到的地址只能是站内路径：拒绝 //evil.com、https://… 这类外部地址（开放重定向），
 * 也不回到登录页本身。
 */
export function safeRedirect(target) {
  const ok = typeof target === 'string' && target.startsWith('/') && !target.startsWith('//') && !target.startsWith('/login')
  return ok ? target : '/chat'
}

/**
 * 路由守卫：未登录去登录页并记住原地址；有令牌但还没取用户信息时先取。
 * 只有 401（令牌失效）才清掉登录态；网络不通、超时、网关 5xx 只提示并放行，
 * 否则网关抖一下学生就被登出（profile 仍为空，下次导航会再取）。
 * @param {() => { isLoggedIn: boolean, profile: object|null, loadProfile: Function, reset: Function }} getUser
 */
export function createAuthGuard(getUser, notify) {
  return async (to) => {
    if (to.meta?.public) {
      return true
    }
    // 手输或误改地址（/chat/abc、?lesson=abc）在后端会被当成参数错误（400），这里先按「不存在」处理
    if (to.params?.id !== undefined && !ID_PATTERN.test(String(to.params.id))) {
      notify?.('warning', '页面地址不正确，已回到新对话')
      return '/chat'
    }
    const user = getUser()
    const toLogin = { name: 'login', query: { redirect: to.fullPath } }
    if (!user.isLoggedIn) {
      return toLogin
    }
    if (!user.profile) {
      try {
        await user.loadProfile()
      } catch (error) {
        if (error?.code === 401) {
          user.reset()
          return toLogin
        }
        notify?.('warning', error?.message || '获取用户信息失败，请稍后重试')
      }
    }
    return true
  }
}
