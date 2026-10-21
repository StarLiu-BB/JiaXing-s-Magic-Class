/** 19 位以内纯数字：雪花 ID 在 URL 里的合法形式。不合法时按「不存在」处理，不去请求后端 */
export const ID_PATTERN = /^\d{1,19}$/

/** 只用来解析相对地址的占位源：解析结果仍是这个源，才说明目标是站内路径 */
const SAME_ORIGIN = 'http://zhixue.invalid'

/**
 * 登录后要回到的地址只能是站内路径：拒绝 //evil.com、https://… 这类外部地址（开放重定向），
 * 也不回到登录页本身。按浏览器的 URL 规则解析后再判断，因为浏览器会把 \ 当作 /、去掉制表符和换行，
 * 「/\evil.com」「/<Tab>/evil.com」解析出来都是 //evil.com。
 */
export function safeRedirect(target) {
  if (typeof target !== 'string' || !target.startsWith('/')) {
    return '/chat'
  }
  let url
  try {
    url = new URL(target, SAME_ORIGIN)
  } catch {
    return '/chat'
  }
  if (url.origin !== SAME_ORIGIN || url.pathname === '/login' || url.pathname.startsWith('/login/')) {
    return '/chat'
  }
  return url.pathname + url.search + url.hash
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
