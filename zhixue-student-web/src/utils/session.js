/**
 * 登录失效的统一出口。请求层只负责「发现」失效，怎么处理（清登录态、带上原地址去登录页）
 * 由 main.js 注册——这样请求层不依赖路由，也能单独测试。
 */
let handler = () => {}

export function setSessionExpiredHandler(fn) {
  handler = fn
}

export function sessionExpired() {
  handler()
}
